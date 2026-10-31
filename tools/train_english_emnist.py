"""Train English letter model on EMNIST (optimized: pre-process all samples).

Pre-processes all EMNIST samples to 64x64 tensors at init time,
so __getitem__ only does tensor augmentation (fast).
"""
import argparse, math, sys, time
from pathlib import Path

import numpy as np
from PIL import Image
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torchvision import datasets

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from src.models import build_model, count_params

CANVAS = 64
CONTENT = 56


class EMNISTPreprocessed(Dataset):
    """EMNIST Letters pre-processed to [1,64,64] tensors at init."""

    def __init__(self, root, train=True, augment=False):
        print(f"  loading EMNIST letters (train={train})...")
        emnist = datasets.EMNIST(root=root, split="letters",
                                  train=train, download=True)
        n = len(emnist)
        print(f"  pre-processing {n} samples to 64x64 tensors...")
        self.data = np.zeros((n, 1, CANVAS, CANVAS), dtype=np.float32)
        self.labels = np.zeros(n, dtype=np.int64)
        for i in range(n):
            img, label = emnist[i]
            arr = np.array(img, dtype=np.uint8).T  # EMNIST transposed
            self.data[i, 0] = self._preprocess(arr)
            self.labels[i] = label - 1  # 1-26 -> 0-25
            if (i + 1) % 20000 == 0:
                print(f"    {i+1}/{n}")
        print(f"  done: {n} samples")
        if augment:
            from torchvision.transforms import v2
            self.aug = v2.Compose([
                v2.RandomAffine(degrees=10, translate=(0.05, 0.05),
                                scale=(0.85, 1.15), fill=0),
                v2.RandomErasing(p=0.2, scale=(0.02, 0.10), value=0),
            ])
        else:
            self.aug = None

    @staticmethod
    def _preprocess(arr):
        ys, xs = np.where(arr > 30)
        if len(xs) == 0:
            return np.zeros((CANVAS, CANVAS), dtype=np.float32)
        cropped = arr[ys.min():ys.max()+1, xs.min():xs.max()+1]
        ch, cw = cropped.shape
        scale = CONTENT / max(ch, cw)
        nw = max(1, round(cw * scale))
        nh = max(1, round(ch * scale))
        pil = Image.fromarray(cropped).resize((nw, nh), Image.BILINEAR)
        canvas = np.zeros((CANVAS, CANVAS), dtype=np.uint8)
        ox, oy = (CANVAS - nw) // 2, (CANVAS - nh) // 2
        canvas[oy:oy+nh, ox:ox+nw] = np.array(pil, dtype=np.uint8)
        return canvas.astype(np.float32) / 255.0

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, idx):
        t = torch.from_numpy(self.data[idx])
        if self.aug is not None:
            t = self.aug(t)
        return t, int(self.labels[idx])


def evaluate(model, loader, device):
    model.eval()
    correct = total = 0
    with torch.no_grad():
        for x, y in loader:
            x, y = x.to(device), y.to(device)
            correct += (model(x).argmax(1) == y).sum().item()
            total += y.size(0)
    return correct / max(1, total)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--data-root", type=str, default="data/emnist")
    parser.add_argument("--out-dir", type=str, default="runs/english")
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    print("building train dataset...")
    train_ds = EMNISTPreprocessed(args.data_root, train=True, augment=False)
    print("building test dataset...")
    test_ds = EMNISTPreprocessed(args.data_root, train=False, augment=False)
    print(f"train: {len(train_ds)}, test: {len(test_ds)}")

    train_loader = DataLoader(train_ds, batch_size=args.batch_size,
                              shuffle=True, num_workers=0, drop_last=True)
    test_loader = DataLoader(test_ds, batch_size=args.batch_size * 2,
                             shuffle=False, num_workers=0)

    model = build_model("plain_cnn", num_classes=26).to(device)
    print(f"model: plain_cnn, params={count_params(model):,}")

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)
    total_steps = len(train_loader) * args.epochs
    warmup = len(train_loader) * min(5, args.epochs)

    best = 0.0
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    gs = 0

    for ep in range(args.epochs):
        model.train()
        t0 = time.time()
        rloss, n = 0.0, 0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            if gs < warmup:
                lr = args.lr * (gs + 1) / max(1, warmup)
            else:
                p = (gs - warmup) / max(1, total_steps - warmup)
                lr = 0.5 * args.lr * (1 + math.cos(math.pi * p))
            for pg in optimizer.param_groups:
                pg["lr"] = lr
            optimizer.zero_grad()
            loss = criterion(model(x), y)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            rloss += loss.item() * y.size(0)
            n += y.size(0)
            gs += 1
        acc = evaluate(model, test_loader, device)
        el = time.time() - t0
        print(f"epoch {ep+1}/{args.epochs}: loss={rloss/n:.4f} "
              f"acc={acc:.4f} time={el:.1f}s lr={lr:.2e}")
        torch.save({"model": model.state_dict(), "epoch": ep, "acc": acc},
                    out / "latest.pt")
        if acc > best:
            best = acc
            torch.save({"model": model.state_dict(), "epoch": ep, "acc": acc},
                        out / "best.pt")
            print(f"  new best: {acc:.4f}")
    print(f"done. best acc: {best:.4f}")


if __name__ == "__main__":
    main()