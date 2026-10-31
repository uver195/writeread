"""Train a 26-class English letter recognition model aligned with HCCR pipeline.

EMNIST Letters (A-Z) -> 64x64 HCCR preprocessing -> PlainCNN(26) -> checkpoint.
Output: runs/english/best.pt
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

CANVAS_SIZE = 64
CONTENT_SIZE = 56
FG_THRESHOLD = 30


class EMNISTHCCRDataset(Dataset):
    """EMNIST Letters -> 64x64 tensor aligned with HCCR preprocess.c output."""

    def __init__(self, root, train=True, augment=False):
        self.emnist = datasets.EMNIST(
            root=root, split="letters", train=train, download=True)
        self.augment = augment
        if augment:
            from torchvision.transforms import v2
            self.aug = v2.Compose([
                v2.RandomAffine(degrees=10, translate=(0.05, 0.05),
                                scale=(0.85, 1.15), fill=0),
                v2.RandomErasing(p=0.2, scale=(0.02, 0.10), value=0),
            ])
        else:
            self.aug = None

    def __len__(self):
        return len(self.emnist)

    def __getitem__(self, idx):
        img, label = self.emnist[idx]
        label = label - 1  # EMNIST letters: 1-26 -> 0-25
        arr = np.array(img, dtype=np.uint8)  # [28,28] black bg white stroke
        arr = arr.T  # EMNIST images are transposed
        tensor = self._preprocess(arr)
        if self.aug is not None:
            tensor = self.aug(tensor)
        return tensor, label

    def _preprocess(self, arr):
        """28x28 uint8 (stroke=white) -> [1,64,64] float (stroke~1.0, bg=0.0)."""
        ys, xs = np.where(arr > FG_THRESHOLD)
        if len(xs) == 0:
            return torch.zeros(1, CANVAS_SIZE, CANVAS_SIZE)
        cropped = arr[ys.min():ys.max()+1, xs.min():xs.max()+1]
        ch, cw = cropped.shape
        max_dim = max(ch, cw)
        scale = CONTENT_SIZE / max_dim
        new_w = max(1, round(cw * scale))
        new_h = max(1, round(ch * scale))
        pil = Image.fromarray(cropped)
        pil = pil.resize((new_w, new_h), Image.BILINEAR)
        resized = np.array(pil, dtype=np.uint8)
        canvas = np.zeros((CANVAS_SIZE, CANVAS_SIZE), dtype=np.uint8)
        off_x = (CANVAS_SIZE - new_w) // 2
        off_y = (CANVAS_SIZE - new_h) // 2
        canvas[off_y:off_y+new_h, off_x:off_x+new_w] = resized
        t = torch.from_numpy(canvas.astype(np.float32) / 255.0)
        return t.unsqueeze(0)


def evaluate(model, loader, device):
    model.eval()
    correct = total = 0
    with torch.no_grad():
        for x, y in loader:
            x, y = x.to(device), y.to(device)
            pred = model(x).argmax(1)
            correct += (pred == y).sum().item()
            total += y.size(0)
    return correct / max(1, total)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--data-root", type=str, default="data/emnist")
    parser.add_argument("--out-dir", type=str, default="runs/english")
    parser.add_argument("--no-augment", action="store_true")
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    train_ds = EMNISTHCCRDataset(args.data_root, train=True,
                                  augment=not args.no_augment)
    test_ds = EMNISTHCCRDataset(args.data_root, train=False, augment=False)
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
    warmup_steps = len(train_loader) * min(5, args.epochs)

    best_acc = 0.0
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    global_step = 0

    for epoch in range(args.epochs):
        model.train()
        t0 = time.time()
        running_loss = 0.0
        n = 0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            if global_step < warmup_steps:
                lr = args.lr * (global_step + 1) / max(1, warmup_steps)
            else:
                prog = (global_step - warmup_steps) / max(1, total_steps - warmup_steps)
                lr = 0.5 * args.lr * (1 + math.cos(math.pi * prog))
            for pg in optimizer.param_groups:
                pg["lr"] = lr
            optimizer.zero_grad()
            loss = criterion(model(x), y)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            running_loss += loss.item() * y.size(0)
            n += y.size(0)
            global_step += 1
        acc = evaluate(model, test_loader, device)
        elapsed = time.time() - t0
        print(f"epoch {epoch+1}/{args.epochs}: loss={running_loss/n:.4f} "
              f"test_acc={acc:.4f} time={elapsed:.1f}s lr={lr:.2e}")
        torch.save({"model": model.state_dict(), "epoch": epoch, "acc": acc},
                    out / "latest.pt")
        if acc > best_acc:
            best_acc = acc
            torch.save({"model": model.state_dict(), "epoch": epoch, "acc": acc},
                        out / "best.pt")
            print(f"  new best: {acc:.4f}")
    print(f"done. best acc: {best_acc:.4f}")


if __name__ == "__main__":
    main()