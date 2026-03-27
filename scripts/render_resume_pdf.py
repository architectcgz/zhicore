#!/usr/bin/env python3
from pathlib import Path

import fitz


HTML_PATH = Path("/home/azhi/workspace/projects/zhicore-microservice/docs/简历-陈志峰.html")
PDF_PATH = Path("/home/azhi/workspace/projects/zhicore-microservice/docs/简历-陈志峰.pdf")
FONT_DIR = "/usr/share/fonts/opentype/noto"
FONT_FILE = "NotoSansCJK-Regular.ttc"


def main() -> None:
    html = HTML_PATH.read_text(encoding="utf-8")
    css = f"""
    @font-face {{
      font-family: noto_resume;
      src: url({FONT_FILE});
    }}
    body {{
      font-family: noto_resume;
    }}
    """

    doc = fitz.open()
    page = doc.new_page(width=595.28, height=841.89)
    rect = fitz.Rect(38, 28, 557, 814)
    archive = fitz.Archive(FONT_DIR)
    spare_height, _ = page.insert_htmlbox(rect, html, css=css, archive=archive)
    if spare_height < 0:
      raise RuntimeError(f"resume content overflowed A4 page by {-spare_height:.2f} pt")
    doc.save(PDF_PATH, garbage=3, deflate=True)
    doc.close()
    print(PDF_PATH)


if __name__ == "__main__":
    main()
