import os, requests

path = "data/emnist/EMNIST/raw/gzip.zip"
size = os.path.getsize(path)
print(f"local file: {size} bytes ({size/1e6:.1f} MB)")

with open(path, "rb") as f:
    header = f.read(4)
    print(f"header: {header.hex()} (504b0304 = valid zip)")
    f.seek(-22, 2)
    tail = f.read(4)
    print(f"tail: {tail.hex()} (504b0506 = valid zip end)")

try:
    r = requests.head("https://www.itl.nist.gov/iaui/vip/cs_links/EMNIST/gzip.zip",
                       allow_redirects=True, timeout=15)
    cl = r.headers.get("Content-Length", "unknown")
    print(f"server Content-Length: {cl} ({int(cl)/1e6:.1f} MB)" if cl != "unknown" else f"server Content-Length: {cl}")
    print(f"server status: {r.status_code}")
    print(f"accept-ranges: {r.headers.get('Accept-Ranges', 'none')}")
except Exception as e:
    print(f"head request failed: {e}")