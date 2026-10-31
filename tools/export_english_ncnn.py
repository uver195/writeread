"""Export English letter model (PlainCNN-26) to NCNN format.

Usage: python tools/export_english_ncnn.py
Input:  runs/english/best.pt
Output: models/English/ncnn/english.ncnn.param, english.ncnn.bin
"""
import subprocess, sys
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from src.models import build_model

ROOT = Path(__file__).resolve().parent.parent


def main():
    ckpt_path = ROOT / "runs" / "english" / "best.pt"
    if not ckpt_path.exists():
        print(f"checkpoint not found: {ckpt_path}")
        sys.exit(1)

    ckpt = torch.load(str(ckpt_path), map_location="cpu", weights_only=False)
    acc = ckpt.get("acc", "?")
    print(f"loaded checkpoint: epoch={ckpt.get('epoch')} acc={acc}")

    model = build_model("plain_cnn", num_classes=26)
    model.load_state_dict(ckpt["model"])
    model.eval()

    dummy = torch.randn(1, 1, 64, 64)
    traced = torch.jit.trace(model, dummy)
    traced_path = ROOT / "runs" / "english" / "english_traced.pt"
    traced.save(str(traced_path))
    print(f"traced model saved: {traced_path}")

    out_dir = ROOT / "models" / "English" / "ncnn"
    out_dir.mkdir(parents=True, exist_ok=True)
    param_path = out_dir / "english.ncnn.param"
    bin_path = out_dir / "english.ncnn.bin"

    cmd = [
        "pnnx",
        str(traced_path),
        "inputshape=[1,1,64,64]",
        f"ncnnparam={param_path}",
        f"ncnnbin={bin_path}",
        "fp16=1",
        "optlevel=2",
        "device=cpu",
    ]
    print("running pnnx...")
    result = subprocess.run(cmd, capture_output=True, text=True, cwd=str(ROOT))
    if result.stdout:
        print(result.stdout[-800:])
    if result.returncode != 0:
        print(f"pnnx failed (exit {result.returncode})")
        if result.stderr:
            print(result.stderr[-500:])
        sys.exit(1)

    if param_path.exists() and bin_path.exists():
        print(f"NCNN export OK:")
        print(f"  param: {param_path} ({param_path.stat().st_size:,} bytes)")
        print(f"  bin:   {bin_path} ({bin_path.stat().st_size:,} bytes)")
    else:
        print("ERROR: NCNN output files not found")
        sys.exit(1)


if __name__ == "__main__":
    main()