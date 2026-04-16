# PPTXDown

PPTXDown converts PowerPoint `.pptx` files to GitShow-compatible Markdown.

(c) 2026 Radek Burget (burgetr@fit.vut.cz)

## Usage

```bash
java -jar pptxdown.jar <input.pptx> <output_dir>
```

The output directory will contain:

- `content.md` — Markdown transcript of the presentation (slides separated by `---`)
- `assets/` — Extracted images and other referenced files.

## Build

Requires Java 21 and Maven.

```bash
mvn package
```

This produces a self-contained `target/pptxdown.jar`.
