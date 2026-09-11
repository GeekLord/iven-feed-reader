package com.iven.lfflfeedreader.domparser;

import android.content.res.Resources;
import android.os.Build;

import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Parses RSS feeds into the app's serializable feed model. */
public class DOMParser {

    private static final int NETWORK_TIMEOUT_MS = 15_000;

    public RSSFeed parseXml(String xml) {
        RSSFeed feed = new RSSFeed();
        if (xml == null || xml.trim().isEmpty()) {
            return feed;
        }

        try {
            URL url = new URL(xml);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            configureSecureParser(factory);
            DocumentBuilder builder = factory.newDocumentBuilder();

            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(NETWORK_TIMEOUT_MS);

            try (InputStream stream = connection.getInputStream()) {
                Document document = builder.parse(new InputSource(stream));
                document.getDocumentElement().normalize();
                parseItems(document.getElementsByTagName("item"), feed);
            }
        } catch (MalformedURLException ignored) {
            // Invalid custom-feed URLs are represented by an empty feed.
        } catch (Exception ignored) {
            // Network and malformed-feed failures are represented by an empty/partial feed.
        }
        return feed;
    }

    private void configureSecureParser(DocumentBuilderFactory factory) {
        // Android's built-in DOM factory rejects most XML feature flags. Apply every
        // hardening option independently so an unsupported option cannot prevent
        // otherwise valid feeds from being parsed.
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        try {
            factory.setXIncludeAware(false);
        } catch (UnsupportedOperationException ignored) {
            // Not implemented by the platform parser.
        }
        factory.setExpandEntityReferences(false);
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Different Android releases support different subsets of XML features.
        }
    }

    private void parseItems(NodeList nodes, RSSFeed feed) throws Exception {
        for (int i = 0; i < nodes.getLength(); i++) {
            RSSItem item = new RSSItem();
            NodeList children = nodes.item(i).getChildNodes();

            for (int j = 0; j < children.getLength(); j++) {
                Node node = children.item(j);
                if (node == null) {
                    continue;
                }

                String value = node.getTextContent();
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                setItemValue(item, node.getNodeName(), value.trim());
            }
            if (item.getDate().matches(".*\\d{2}:\\d{2}$")) {
                feed.addItem(item);
            }
        }
    }

    private void setItemValue(RSSItem item, String nodeName, String value) throws Exception {
        if ("title".equals(nodeName)) {
            item.setTitle(value);
        } else if ("link".equals(nodeName)) {
            item.setLink(value);
        } else if ("content:encoded".equals(nodeName)) {
            item.setCompleteDescription(value);
            item.setImage2(extractImageUrl(value));
        } else if ("description".equals(nodeName)) {
            item.setDescription(value);
            item.setImage(extractImageUrl(value));
        } else if ("pubDate".equals(nodeName)) {
            String formattedDate = formatDate(value);
            if (formattedDate != null) {
                item.setDate(formattedDate);
            }
        }
    }

    private String extractImageUrl(String html) {
        Elements images = Jsoup.parse(html).select("img");
        return images.isEmpty() ? "" : images.first().attr("src").trim();
    }

    private String formatDate(String value) {
        try {
            String formattedDate = value.replace(" +0000", "");
            SimpleDateFormat input = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
            input.setLenient(false);
            Date date = input.parse(formattedDate);
            if (date == null) {
                return null;
            }

            Locale locale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Resources.getSystem().getConfiguration().getLocales().get(0)
                    : Resources.getSystem().getConfiguration().locale;
            SimpleDateFormat output = new SimpleDateFormat("EEE, dd.MM.yyyy - HH:mm", locale);
            output.setTimeZone(TimeZone.getDefault());
            return output.format(date);
        } catch (Exception ignored) {
            return null;
        }
    }
}
