"""Download EMNIST gzip.zip from scratch (server has no resume support)."""
import os, sys, time, requests

URL = "https://www.itl.nist.gov/iaui/vip/cs_links/EMNIST/gzip.zip"
PATH = "data/emnist/EMNIST/raw/gzip.zip"

os.makedirs(os.path.dirname(PATH), exist_ok=True)

# Delete incomplete file
if os.path.exists(PATH):
    os.remove(PATH)
    print("deleted incomplete file")

retry = 0
while retry < 200:
    try:
        print(f"attempt {retry+1}, downloading from scratch...")
        r = requests.get(URL, stream=True, timeout=60)
        if r.status_code != 200:
            print(f"HTTP {r.status_code}, retry in 10s")
            time.sleep(10)
            retry += 1
            continue
        total = int(r.headers.get("content-length", 0))
        print(f"total size: {total/1e6:.1f}MB")
        downloaded = 0
        t0 = time.time()
        with open(PATH, "wb") as f:
            for chunk in r.iter_content(chunk_size=512*1024):
                f.write(chunk)
                downloaded += len(chunk)
                if downloaded % (50*1024*1024) < 512*1024:
                    elapsed = time.time() - t0
                    speed = downloaded / max(1, elapsed) / 1e6
                    pct = downloaded / max(1, total) * 100
                    print(f"{downloaded/1e6:.0f}MB / {total/1e6:.0f}MB ({pct:.0f}%) {speed:.2f}MB/s")
        final = os.path.getsize(PATH)
        print(f"download finished: {final/1e6:.1f}MB")
        if total > 0 and final >= total:
            print("COMPLETE!")
            sys.exit(0)
        elif total == 0 and final > 100*1024*1024:
            print("no content-length but got large file, assuming complete")
            sys.exit(0)
        else:
            print(f"incomplete ({final} vs {total}), retry")
            retry += 1
            time.sleep(5)
    except Exception as e:
        retry += 1
        saved = os.path.getsize(PATH) if os.path.exists(PATH) else 0
        print(f"error at {saved/1e6:.1f}MB: {e}, retry {retry} in 10s")
        time.sleep(10)

print(f"gave up after {retry} retries")
sys.exit(1)