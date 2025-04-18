package net.dongliu.apk.parser.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * fetch dependency resource file from android source
 *
 * @author Liu Dong dongliu@live.cn
 */
public class ResourceFetcher {

    /**
     * from https://android.googlesource.com/platform/frameworks/base/+/master/core/res/res/values/public.xml
     */
    private void fetchSystemAttrIds()
            throws IOException, SAXException, ParserConfigurationException {
        final String url = "https://android.googlesource.com/platform/frameworks/base/+/master/core/res/res/values/public.xml";
        final String html = this.getUrl(url);
        final String xml = this.retrieveCode(html);
        if (xml != null) {
            this.parseAttributeXml(xml);
        }
    }

    private void parseAttributeXml(@NonNull final String xml)
            throws IOException, ParserConfigurationException, SAXException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser = factory.newSAXParser();
        final List<Pair<Integer, String>> attrIds = new ArrayList<>();
        final DefaultHandler dh = new DefaultHandler() {
            @Override
            public void startElement(final String uri, final String localName, final String qName,
                                     final Attributes attributes) {
                if (!qName.equals("public")) {
                    return;
                }
                final String type = attributes.getValue("type");
                if (type == null) {
                    return;
                }
                if (type.equals("attr")) {
                    //attr ids.
                    String idStr = attributes.getValue("id");
                    if (idStr == null) {
                        return;
                    }
                    final String name = attributes.getValue("name");
                    if (idStr.startsWith("0x")) {
                        idStr = idStr.substring(2);
                    }
                    final int id = Integer.parseInt(idStr, 16);
                    attrIds.add(new Pair<>(id, name));
                }
            }
        };
        parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), dh);
        for (final Pair<Integer, String> pair : attrIds) {
            System.out.printf("%s=%d%n", pair.getRight(), pair.getLeft());
        }
    }

    /**
     * the android system r style.
     * see http://developer.android.com/reference/android/R.style.html
     * from https://android.googlesource.com/platform/frameworks/base/+/master/api/current.txt r.style section
     */
    private void fetchSystemStyle() throws IOException {
        final String url = "https://android.googlesource.com/platform/frameworks/base/+/master/api/current.txt";
        final String html = this.getUrl(url);
        final String code = this.retrieveCode(html);
        if (code == null) {
            System.err.println("code area not found");
            return;
        }
        final int begin = code.indexOf("R.style");
        final int end = code.indexOf("}", begin);
        final String styleCode = code.substring(begin, end);
        final String[] lines = styleCode.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("field public static final")) {
                line = Strings.substringBefore(line, ";").replace("deprecated ", "")
                        .substring("field public static final int ".length()).replace("_", ".");
                System.out.println(line);
            }
        }
    }

    @NonNull
    private String getUrl(final String url) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(10000);
            final byte[] bytes = Inputs.readAllAndClose(conn.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    @Nullable
    private String retrieveCode(@NonNull final String html) {
        final Matcher matcher = Pattern.compile("<ol class=\"prettyprint\">(.*?)</ol>").matcher(html);
        if (matcher.find()) {
            final String codeHtml = matcher.group(1);
            if (codeHtml == null)
                return null;
            return codeHtml.replace("</li>", "\n").replaceAll("<[^>]+>", "").replace("&lt;", "<")
                    .replace("&quot;", "\"").replace("&gt;", ">");
        } else {
            return null;
        }
    }

    public static void main(final String[] args)
            throws ParserConfigurationException, SAXException, IOException {
        final ResourceFetcher fetcher = new ResourceFetcher();
        fetcher.fetchSystemAttrIds();
        //fetcher.fetchSystemStyle();
    }
}
