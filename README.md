# Secure PDF Tool

A lightweight, offline desktop PDF utility built with **Java 17+**, **JavaFX**, and **Apache PDFBox**.

The tool is intended for local/private PDF operations such as adding text, hiding areas, merging, splitting, extracting pages, rotating pages, and password-protecting PDFs without uploading documents to any cloud service.

---

## Features

### Visual PDF Editing

* Open and preview PDF files
* Navigate between pages
* Zoom in/out
* Add custom text on PDF pages
* Select, move, and delete added text
* Choose font family and font size
* Add blank/white hide areas over existing content
* Select, move, and delete added hide areas
* Save changes to the existing PDF
* Save changes as a new PDF

### PDF Utilities

* Merge multiple PDFs
* Split PDF into individual pages
* Extract selected page ranges
* Rotate pages
* Add text watermark/stamp
* Password-protect PDF
* View basic PDF information

---

## Important Limitation

This tool currently **adds overlay content** to a PDF.

For example:

* Added text is written on top of the PDF.
* Blank/hide area places a white rectangle over existing content.

It does **not** fully edit the original internal PDF text structure like Adobe Acrobat or PDF-XChange Editor.

Also, the **Blank/Hide Area** feature is not secure redaction. It visually hides content but may not permanently remove underlying text from the PDF. Do not use it for legal, confidential, or compliance-grade redaction unless proper redaction support is added.

---

## Technology Stack

* Java 17+
* JavaFX
* Apache PDFBox
* Maven

---

## Requirements

Install the following:

* JDK 17 or higher
  Recommended: JDK 17 or JDK 19
* Maven 3.8+
* Windows, macOS, or Linux

For Windows font support, Arial and Calibri are loaded from:

```text
C:\Windows\Fonts
```

If Arial or Calibri are not available, the application falls back to standard PDF fonts.

---

## Build

From the project root:

```bash
mvn clean package
```

After a successful build, the output will be available under:

```text
target/
```

Expected output:

```text
target/secure-pdf-tool-1.6.0.jar
target/lib/
```

---

## Run from Maven

```bash
mvn clean javafx:run
```

---

## Run Packaged JAR

Make sure Java 17+ is available in PATH:

```bash
java -version
```

Then run:

```bash
java -cp "target\secure-pdf-tool-1.6.0.jar;target\lib\*" com.localpdftool.Launcher
```

On Windows, you can also use:

```bat
run-pdf-tool.bat
```

---

## Eclipse Setup

1. Extract or clone the project.
2. Open Eclipse.
3. Go to:

```text
File → Import → Maven → Existing Maven Projects
```

4. Select the project folder containing `pom.xml`.
5. Click **Finish**.
6. Set JDK 17+:

```text
Window → Preferences → Java → Installed JREs
```

7. Right-click project:

```text
Maven → Update Project
```

8. Run using Maven goal:

```text
clean javafx:run
```

---

## Basic Usage

### Add Text

1. Open the **Visual Edit** tab.
2. Click **Open PDF**.
3. Enter the text you want to add.
4. Select font and size.
5. Click **Add Text**.
6. Click on the PDF page where the text should appear.
7. Select and drag the added text if position adjustment is needed.
8. Click **Save** or **Save As PDF**.

### Move Added Text

1. Click the added text.
2. Drag it to the required position.
3. Release the mouse.
4. Save the PDF.

### Delete Added Text

1. Click the added text.
2. Click **Delete Selected**.

### Hide Existing Text / Area

1. Click **Blank/Hide Area**.
2. Drag a rectangle over the area you want to hide.
3. Adjust by selecting and moving the hide area.
4. Save the PDF.

Note: this is visual hiding, not secure redaction.

---

## Save Options

### Save

Overwrites the currently opened PDF after confirmation.

### Save As PDF

Saves the edited PDF as a new file.

---

## Project Structure

```text
secure-pdf-tool/
├── pom.xml
├── run-pdf-tool.bat
├── package-and-run.bat
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── localpdftool/
│                   ├── Launcher.java
│                   └── SecurePdfToolApp.java
└── target/
```

---

## Security / Privacy

This tool is designed to work locally.

* No cloud upload
* No account/login
* No AI processing
* No external server dependency
* PDF files are processed on the local machine

However, always review generated PDFs before sharing externally.

---

## Known Limitations

* Does not directly edit existing PDF text objects
* Does not provide secure redaction yet
* Does not support OCR editing
* Does not currently support image signatures
* Does not support drag-and-drop page reordering yet
* UI is functional but not yet polished like commercial PDF editors

---

## Possible Future Enhancements

* Secure redaction support
* Image/signature insertion
* Page thumbnails
* Drag-and-drop page reorder
* OCR for scanned PDFs
* Export flattened PDF
* Windows `.exe` packaging using `jpackage`
* Dark mode
* Better annotation tools
* Highlight, underline, rectangle, and arrow tools

---

## License

Add your preferred license here.

Example:

```text
MIT License
```

---

## Disclaimer

This project is intended as a local PDF utility and learning/customization tool. It should not be treated as a replacement for enterprise-grade PDF software for legal, regulatory, or compliance-grade document processing.
