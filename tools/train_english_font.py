"""Train English letter model using system fonts (no external download).

Generates synthetic handwritten-like letters from Windows TTF fonts with
augmentation, aligned with HCCR preprocessing (64x64, stroke=1.0 bg=0.0).
"""
import argparse, math, random, sys, time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from src.models import build_model, count_params

CANVAS_SIZE = 64
CONTENT_SIZE = 56
LETTERS = [chr(ord("A") + i) for i in range(26)]
FONTS_DIR = Path("C:/Windows/Fonts")
RENDER_SIZE = 80


def find_fonts():
    fonts = []
    for f in sorted(FONTS_DIR.glob("*.ttf")):
        name = f.name.lower()
        if any(x in name for x in ["symbol", "wing", "webdings", "marlett"]):
            continue
        try:
            ImageFont.truetype(str(f), 40)
            fonts.append(f)
        except Exception:
            pass
        if len(fonts) >= 10:
            break
    return fonts


class FontLetterDataset(Dataset):
    def __init__(self, fonts, num_per_letter=40, train=True, seed=42):
        self.train = train
        self.samples = []
        rng = random.Random(seed)
        for font_path in fonts:
            for label, letter in enumerate(LETTERS):
                for _ in range(num_per_letter):
                    arr = self._render(letter, font_path, rng)
                    tensor = self._preprocess(arr)
                    self.samples.append((tensor, label))
        if train:
            from torchvision.transforms import v2
            self.aug = v2.Compose([
                v2.RandomAffine(degrees=15, translate=(0.08, 0.08),
                                scale=(0.8, 1.2), fill=0),
                v2.RandomErasing(p=0.15, scale=(0.02, 0.08), value=0),
            ])
        else:
            self.aug = None
        print(f"  dataset: {len(self.samples)} samples (train={train})")

    def _render(self, letter, font_path, rng):
        size = rng.randint(45, 70)
        font = ImageFont.truetype(str(font_path), size)
        img = Image.new("L", (RENDER_SIZE, RENDER_SIZE), 0)
        draw = ImageDraw.Draw(img)
        bbox = draw.textbbox((0, 0), letter, font=font)
        w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
        x = (RENDER_SIZE - w) // 2 - bbox[0]
        y = (RENDER_SIZE - h) // 2 - bbox[1]
        draw.text((x, y), letter, fill=255, font=font)
        if rng.random() < 0.3:
            img = img.filter(ImageFilter.MaxFilter(3))
        elif rng.random() < 0.3:
            img = img.filter(ImageFilter.MinFilter(3))
        angle = rng.uniform(-12, 12)
        img = img.rotate(angle, fillcolor=0)
        return np.array(img, dtype=np.uint8)

    def _preprocess(self, arr):
        ys, xs = np.where(arr > 30)
        if len(xs) == 0:
            return torch.zeros(1, CANVAS_SIZE, CANVAS_SIZE)
        cropped = arr[ys.min():ys.max()+1, xs.min():xs.max()+1]
        ch, cw = cropped.shape
        max_dim = max(ch, cw)
        scale = CONTENT_SIZE / max_dim
        nw, nh = max(1, round(cw * scale)), max(1, round(ch * scale))
        pil = Image.fromarray(cropped).resize((nw, nh), Image.BILINEAR)
        resized = np.array(pil, dtype=np.uint8)
        canvas = np.zeros((CANVAS_SIZE, CANVAS_SIZE), dtype=np.uint8)
        ox, oy = (CANVAS_SIZE - nw) // 2, (CANVAS_SIZE - nh) // 2
        canvas[oy:oy+nh, ox:ox+nw] = resized
        return torch.from_numpy(canvas.astype(np.float32) / 255.0).unsqueeze(0)

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        tensor, label = self.samples[idx]
        if self.aug is not None:
            tensor = self.aug(tensor)
        return tensor, label


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
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=256)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--num-per-letter", type=int, default=40)
    parser.add_argument("--out-dir", type=str, default="runs/english")
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    fonts = find_fonts()
    print(f"fonts ({len(fonts)}): {[f.name for f in fonts]}")
    print("building train dataset...")
    train_ds = FontLetterDataset(fonts, args.num_per_letter, train=True, seed=42)
    print("building test dataset...")
    test_ds = FontLetterDataset(fonts, max(10, args.num_per_letter // 3),
                                train=False, seed=999)

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
    gs = 0

    for epoch in range(args.epochs):
        model.train()
        t0 = time.time()
        rloss, n = 0.0, 0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device)
            if gs < warmup_steps:
                lr = args.lr * (gs + 1) / max(1, warmup_steps)
            else:
                p = (gs - warmup_steps) / max(1, total_steps - warmup_steps)
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
        print(f"epoch {epoch+1}/{args.epochs}: loss={rloss/n:.4f} "
              f"acc={acc:.4f} time={el:.1f}s lr={lr:.2e}")
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