#!/usr/bin/env python3
"""生成 写读板 的“组词”词典 assets/words.json（v2 质量改进版）。

改进点（相对 v1）：
  1. 排除人名 POS（nr / nrt），避免“卜学亮”之类人名被选为某字组词；
  2. 优先选双字词（“XX的X”读起来最顺，如 中->中国、天->天气）；
     没有合格双字词时才退而取三~四字词；
  3. 设最低词频门槛 MIN_FREQ，过滤极冷僻词，无合格词则留空由 App 回退“X字的X”。
数据源：jieba 词典 https://raw.githubusercontent.com/fxsjy/jieba/master/jieba/dict.txt
输出：android/app/src/main/assets/words.json  { "char": "word" }
"""
import json
import re

CHARSET = "models/Handwritten/ncnn/charset.json"
OUT = "android/app/src/main/assets/words.json"
MIN_FREQ = 30             # 最低词频门槛：低于此值视为冷僻词，不予采用
BAD_POS = {"nr", "nrt"}   # 人名 / 音译人名，排除
cjk = re.compile(r"^[\u4e00-\u9fff]+$")


def main(dict_path):
    with open(CHARSET, encoding="utf-8") as f:
        meta = json.load(f)
    chars = list(meta["char_to_idx"].keys())
    charset = set(chars)
    print(f"charset: {len(chars)} chars")

    two = {}      # char -> [(word, freq)]  仅双字
    multi = {}    # char -> [(word, freq)]  3~6 字
    for line in open(dict_path, encoding="utf-8"):
        parts = line.rstrip("\n").split(" ")
        if len(parts) < 3:
            continue
        w = parts[0].strip()
        if not (2 <= len(w) <= 6) or not cjk.match(w):
            continue
        pos = parts[2].strip()
        if pos in BAD_POS:
            continue
        try:
            freq = int(parts[1])
        except ValueError:
            continue
        if freq < MIN_FREQ:
            continue
        head = w[0]
        if head not in charset:
            continue
        (two if len(w) == 2 else multi).setdefault(head, []).append((w, freq))

    def best(cands):
        return max(cands, key=lambda wf: (wf[1], -len(wf[0]))) if cands else None

    words = {}
    covered = 0
    for c in chars:
        b = best(two.get(c))
        if b is None:
            b = best(multi.get(c))
        if b is None:
            words[c] = ""
        else:
            words[c] = b[0]
            covered += 1
    print(f"covered: {covered}/{len(chars)} = {covered/len(chars)*100:.1f}%")

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(words, f, ensure_ascii=False, separators=(",", ":"))
    print("saved:", OUT)


if __name__ == "__main__":
    import sys
    path = sys.argv[1] if len(sys.argv) > 1 else "jieba_dict.txt"
    main(path)