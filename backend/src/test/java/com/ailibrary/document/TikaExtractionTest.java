package com.ailibrary.document;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the real Tika parser stack (and its commons-compress version) for the allowed formats. */
class TikaExtractionTest {
    @Test
    void extractsTextFromEpub() throws Exception {
        List<Document> docs = new TikaDocumentReader(new ByteArrayResource(epub()) {
            @Override public String getFilename() { return "book.epub"; }
        }).read();
        assertThat(docs).isNotEmpty();
        assertThat(docs.getFirst().getText()).contains("Hexagonal architecture isolates the domain");
    }

    @Test
    void extractsTextFromMarkdown() {
        byte[] md = "# Notes\n\nPorts and adapters.".getBytes(StandardCharsets.UTF_8);
        List<Document> docs = new TikaDocumentReader(new ByteArrayResource(md) {
            @Override public String getFilename() { return "notes.md"; }
        }).read();
        assertThat(docs.getFirst().getText()).contains("Ports and adapters.");
    }

    private static byte[] epub() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            byte[] mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry first = new ZipEntry("mimetype");
            first.setMethod(ZipEntry.STORED);
            first.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            first.setCrc(crc.getValue());
            zip.putNextEntry(first);
            zip.write(mimetype);
            zip.closeEntry();
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""");
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">test</dc:identifier><dc:title>Test</dc:title><dc:language>en</dc:language></metadata>
                      <manifest><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/></manifest>
                      <spine><itemref idref="c1"/></spine>
                    </package>""");
            put(zip, "OEBPS/chapter1.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter 1</title></head>
                    <body><p>Hexagonal architecture isolates the domain from adapters.</p></body></html>""");
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
