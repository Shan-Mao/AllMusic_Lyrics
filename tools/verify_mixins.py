#!/usr/bin/env python3
"""
字节码级 Mixin 注入点校验器（无第三方依赖，纯 class 文件解析）。

对已编译的 Mixin 类做静态解析，提取:
  - @Mixin 的 target 类
  - @Inject 的 method 名 + 处理器方法的参数描述符

然后到目标 jar（Minecraft / 前置模组）中确认:
  - target 类存在
  - 被注入的方法名存在
  - 方法描述符的「前 N 个参数」与处理器参数一致
    （Mixin 要求被注入方法的参数是处理器参数的前缀，末尾为 CallbackInfo）

用法:
  python verify_mixins.py <mixin.jar> <target1.jar> [target2.jar ...]
退出码 0 = 全部通过, 1 = 存在问题。
"""
import io
import struct
import sys
import zipfile

UTF8, INTEGER, FLOAT, LONG, DOUBLE, CLASSTAG, STRING = 1, 3, 4, 5, 6, 7, 8
FIELDREF, METHODREF, INTERFACEMETHODREF, NAMEANDTYPE = 9, 10, 11, 12
METHODHANDLE, METHODTYPE, DYNAMIC, INVOKEDYNAMIC, MODULE, PACKAGE = 15, 16, 17, 18, 19, 20
MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;"
INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;"

TRACE = False

findings = []


class ClassFile:
    def __init__(self, data: bytes):
        self.data = data
        self.cp = {}
        self.p = 0
        self._parse()

    def u1(self):
        v = self.data[self.p]
        self.p += 1
        return v

    def u2(self):
        v = struct.unpack_from(">H", self.data, self.p)[0]
        self.p += 2
        return v

    def u4(self):
        v = struct.unpack_from(">I", self.data, self.p)[0]
        self.p += 4
        return v

    def _parse(self):
        assert self.u4() == 0xCAFEBABE, "not a class file"
        self.u2()  # minor
        self.u2()  # major
        count = self.u2()
        i = 1
        while i < count:
            tag = self.u1()
            if tag == UTF8:
                ln = self.u2()
                self.cp[i] = ("utf8", self.data[self.p:self.p + ln].decode("utf-8", "replace"))
                self.p += ln
            elif tag in (INTEGER, FLOAT):
                self.cp[i] = ("int", struct.unpack_from(">i", self.data, self.p)[0])
                self.p += 4
            elif tag in (LONG, DOUBLE):
                self.p += 8
                i += 1
            elif tag in (CLASSTAG, STRING, METHODTYPE, MODULE, PACKAGE):
                self.cp[i] = (tag, self.u2())
            elif tag in (FIELDREF, METHODREF, INTERFACEMETHODREF, NAMEANDTYPE,
                         DYNAMIC, INVOKEDYNAMIC):
                self.cp[i] = (tag, self.u2(), self.u2())
            elif tag == METHODHANDLE:
                self.cp[i] = (tag, self.u1(), self.u2())
            else:
                raise ValueError(f"bad cp tag {tag} at {i}")
            i += 1

        self.u2()  # access flags
        self.this_class = self.u2()
        self.u2()  # super
        self.interfaces = [self.u2() for _ in range(self.u2())]
        self.fields = self._members()
        self.methods = self._members()
        self.class_attrs = self._attributes()

    def _members(self):
        out = []
        for _ in range(self.u2()):
            access = self.u2()
            name_i = self.u2()
            desc_i = self.u2()
            out.append((access, self.utf(name_i), self.utf(desc_i), self._attributes()))
        return out

    def _attributes(self):
        out = {}
        for _ in range(self.u2()):
            name_i = self.u2()
            ln = self.u4()
            body = self.data[self.p:self.p + ln]
            self.p += ln
            out.setdefault(self.utf(name_i), []).append(body)
        return out

    def utf(self, i):
        e = self.cp.get(i)
        return e[1] if e and e[0] == "utf8" else None

    def class_name(self, i):
        e = self.cp.get(i)
        return self.utf(e[1]) if e and e[0] == CLASSTAG else None

    def own_name(self):
        return self.class_name(self.this_class)

    # ---------- 注解 ----------
    # 说明: 注解结构为
    #   annotation     := type_index num_pairs { element_name value }
    #   element_value  := 's' const_index | '@' annotation | '[' n values | ...
    # Mixin 注解的 @Retention 为 CLASS, 因此落在 RuntimeInvisibleAnnotations;
    # 两种属性都要解析。@Inject.method 与 @Inject.at 都是数组形式。
    def all_annotations(self, attrs):
        merged = {}
        for key in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            for body in attrs.get(key, []):
                merged.update(self._parse_annotation_block(body))
        return merged

    def _rd(self, buf, n, what):
        raw = buf.read(n)
        if len(raw) != n:
            raise ValueError(f"注解数据意外结束 (读取 {what})")
        return raw

    def _u2(self, buf, what):
        return struct.unpack(">H", self._rd(buf, 2, what))[0]

    def _parse_annotation_block(self, body):
        """解析属性体内的一到多个注解。"""
        buf = io.BytesIO(body)
        out = {}
        for _ in range(self._u2(buf, "annotation_count")):
            out.update(self._parse_annotation(buf))
        return out

    def _parse_annotation(self, buf):
        ti = self._u2(buf, "type_index")
        desc = self.utf(ti)
        if TRACE:
            print(f"  annotation {desc}")
        vals = {}
        for _ in range(self._u2(buf, "pair_count")):
            name = self.utf(self._u2(buf, "name_index"))
            vals[name] = self._parse_value(buf, name)
        return {desc: vals}

    def _parse_value(self, buf, ctx):
        tag = self._rd(buf, 1, f"tag of {ctx}").decode("latin-1")
        if TRACE:
            print(f"    value {ctx!r} tag={tag!r}")
        if tag == "s":
            return self.utf(self._u2(buf, "const_index"))
        if tag in ("B", "C", "I", "S", "Z"):
            v = struct.unpack(">i", self._rd(buf, 4, "const"))[0]
            return bool(v) if tag == "Z" else v
        if tag == "F":
            return struct.unpack(">f", self._rd(buf, 4, "const"))[0]
        if tag == "D":
            return struct.unpack(">d", self._rd(buf, 8, "const"))[0]
        if tag == "J":
            return struct.unpack(">q", self._rd(buf, 8, "const"))[0]
        if tag == "e":
            t = self.utf(self._u2(buf, "enum_type"))
            c = self.utf(self._u2(buf, "enum_const"))
            return f"{t}.{c}"
        if tag == "c":
            return self.utf(self._u2(buf, "class_info"))
        if tag == "@":
            return self._parse_annotation(buf)
        if tag == "[":
            n = self._u2(buf, "array_count")
            return [self._parse_value(buf, f"{ctx}[{i}]") for i in range(n)]
        raise ValueError(f"未知的 element_value 标签 {tag!r} (元素 {ctx!r})")


def parse_descriptor_args(desc: str):
    """'(Lnet/foo/Bar;I)V' -> ['Lnet/foo/Bar;', 'I']

    也接受裸参数列表 'Lnet/foo/Bar;I'（内部递归时使用）。
    """
    inner = desc[1:desc.index(")")] if desc.startswith("(") else desc
    args, i = [], 0
    while i < len(inner):
        c = inner[i]
        if c == "[":
            j = i + 1
            while inner[j] == "[":
                j += 1
            j = inner.index(";", j) + 1 if inner[j] == "L" else j + 1
            args.append(inner[i:j])
            i = j
        elif c == "L":
            j = inner.index(";", i) + 1
            args.append(inner[i:j])
            i = j
        else:
            args.append(c)
            i += 1
    return args


def verify_target(cf, own, tg, targets):
    if not isinstance(tg, str) or not tg.startswith("L"):
        return
    tg_internal = tg[1:-1]
    tg_dot = tg_internal.replace("/", ".")

    print(f"\n[mixin] {own}")
    print(f"        @Mixin -> {tg_dot}")

    host = next((zf for zf in targets.values()
                 if tg_internal + ".class" in zf.namelist()), None)
    if host is None:
        print("        !! 目标类在给定 jar 中不存在")
        findings.append((own, tg_dot, "MISSING_CLASS", None))
        return

    tcf = ClassFile(host.read(tg_internal + ".class"))
    tmethods = {}
    for _a, mname, mdesc, _at in tcf.methods:
        tmethods.setdefault(mname, []).append(mdesc)

    for _acc, mname, mdesc, mattrs in cf.methods:
        inj = cf.all_annotations(mattrs).get(INJECT_DESC)
        if not inj:
            continue
        spec = inj.get("method")
        specs = spec if isinstance(spec, list) else [spec]
        params = parse_descriptor_args(mdesc)
        # Mixin 约定: 处理器参数 = 被注入方法参数 + 末尾 CallbackInfo
        mixin_params = params[:-1] if params else []
        print(f"        @Inject handler {mname}{mdesc}  at={inj.get('at')}")
        if not params or not params[-1].endswith("CallbackInfo;"):
            print("          !! 处理器最后一个参数不是 CallbackInfo")
            findings.append((own, tg_dot, "NO_CALLBACKINFO", mname))
        for s in specs:
            if not isinstance(s, str):
                continue
            if "(" in s:
                # @Inject 也允许显式写出完整描述符
                base = s[:s.index("(")]
                want = parse_descriptor_args(s)
            else:
                base = s
                want = mixin_params
            if base not in tmethods:
                print(f"          !! 目标方法 '{base}' 不存在")
                findings.append((own, tg_dot, "MISSING_METHOD", base))
                continue
            ok = False
            for td in tmethods[base]:
                if "(" in s:
                    if td == s:
                        ok = True
                        print(f"          OK   {base}{td}  (显式描述符)")
                        break
                else:
                    targs = parse_descriptor_args(td)
                    # 被注入方法的参数必须是处理器参数（去掉 CallbackInfo）的前缀
                    if len(targs) <= len(want) and want[:len(targs)] == targs:
                        ok = True
                        print(f"          OK   {base}{td}")
                        break
            if not ok:
                print("          !! 描述符不匹配")
                print(f"             期望(去掉CallInfo): {want}")
                print(f"             候选              : {tmethods[base]}")
                findings.append((own, tg_dot, "DESC_MISMATCH", base))


def main():
    mixin_jar, target_jars = sys.argv[1], sys.argv[2:]

    targets = {}
    for tj in target_jars:
        targets[tj] = zipfile.ZipFile(tj)

    print(f"=== Mixin jar: {mixin_jar} ===")
    for tj in target_jars:
        print(f"    目标 jar: {tj}")
    mixin_count = 0
    with zipfile.ZipFile(mixin_jar) as mz:
        for n in mz.namelist():
            if not n.endswith(".class"):
                continue
            cf = ClassFile(mz.read(n))
            ann = cf.all_annotations(cf.class_attrs)
            if MIXIN_DESC not in ann:
                continue
            value = ann[MIXIN_DESC].get("value")
            if not value:
                continue
            mixin_count += 1
            for one in (value if isinstance(value, list) else [value]):
                verify_target(cf, cf.own_name(), one, targets)

    print(f"\n=== 结论 (解析到 {mixin_count} 个 @Mixin 类) ===")
    if findings:
        for f in findings:
            print(f"  FAIL {f}")
        print(f"\n{len(findings)} 个问题")
        return 1
    print("  全部注入点校验通过 [OK]")
    return 0


if __name__ == "__main__":
    sys.exit(main())
