import base64
import io
import argparse
from PIL import Image

def png_to_data_url(png_path: str, quality: int = 85, max_width: int = 400) -> str:
    """
    アイコンを Base64 にエンコードする関数
    """
    with Image.open(png_path) as img:
        # リサイズ（アスペクト比維持）
        if img.width > max_width:
            ratio = max_width / img.width
            new_height = int(img.height * ratio)
            img = img.resize((max_width, new_height), Image.LANCZOS)

        if img.mode in ("RGBA", "P"):
            img = img.convert("RGB")

        buffer = io.BytesIO()
        img.save(buffer, format="JPEG", quality=quality)
        jpeg_bytes = buffer.getvalue()

    b64 = base64.b64encode(jpeg_bytes).decode("utf-8")
    return f"data:image/jpeg;base64,{b64}"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("png_path", help="変換するPNGファイルのパス")
    parser.add_argument("output_path", help="変換先のファイル")
    parser.add_argument("--quality", type=int, default=85)
    args = parser.parse_args()

    data_url = png_to_data_url(args.png_path, args.quality)
    with open(args.output_path, "w") as o:
        print(data_url, file=o)
