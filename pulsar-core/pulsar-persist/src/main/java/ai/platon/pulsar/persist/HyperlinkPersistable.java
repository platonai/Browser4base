package ai.platon.pulsar.persist;

import ai.platon.pulsar.persist.model.HyperLinkRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class HyperlinkPersistable implements Comparable<HyperlinkPersistable> {

    public HyperLinkRecord hyperlink;

    private HyperlinkPersistable(@NotNull HyperLinkRecord hyperlink) {
        this.hyperlink = hyperlink;
    }

    public HyperlinkPersistable(@NotNull String url) {
        this(url, null);
    }

    public HyperlinkPersistable(@NotNull String url, @Nullable String text) {
        this(url, text, 0);
    }

    public HyperlinkPersistable(@NotNull String url, @Nullable String text, int order) {
        Objects.requireNonNull(url);

        hyperlink = new HyperLinkRecord();
        hyperlink.setUrl(url);
        hyperlink.setAnchor(text);
        hyperlink.setOrder(order);
    }

    @NotNull
    public static HyperlinkPersistable box(@NotNull HyperLinkRecord hyperlink) {
        return new HyperlinkPersistable(hyperlink);
    }

    @NotNull
    public static HyperlinkPersistable parse(@NotNull String link) {
        String[] urlText = link.split("\\s+");
        if (urlText.length == 1) {
            return new HyperlinkPersistable(urlText[0]);
        } else {
            return new HyperlinkPersistable(urlText[0], urlText[1]);
        }
    }

    public static boolean equals(HyperLinkRecord l, HyperLinkRecord l2) {
        return l.getUrl().equals(l2.getUrl());
    }

    public HyperLinkRecord unbox() {
        return hyperlink;
    }

    public String getUrl() {
        return hyperlink.getUrl();
    }

    public void setUrl(String url) {
        hyperlink.setUrl(url);
    }

    @NotNull
    public String getText() {
        CharSequence anchor = hyperlink.getAnchor();
        return anchor.toString();
    }

    public void setText(@Nullable String text) {
        hyperlink.setAnchor(text);
    }

    public int getOrder() {
        return hyperlink.getOrder();
    }

    public void setOrder(int order) {
        hyperlink.setOrder(order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof HyperlinkPersistable)) {
            return getUrl().equals(o.toString());
        }
        HyperlinkPersistable other = (HyperlinkPersistable) o;
        return getUrl().equals(other.getUrl());
    }

    @Override
    public int hashCode() {
        return getUrl().hashCode();
    }

    @Override
    public int compareTo(@NotNull HyperlinkPersistable hyperLink) {
        int r = getUrl().compareTo(hyperLink.getUrl());
        if (r == 0) {
            r = getText().compareTo(hyperLink.getText());
            if (r == 0) {
                r = getOrder() - hyperLink.getOrder();
            }
        }
        return r;
    }

    @Override
    public String toString() {
        return getUrl() + " " + getText() + " odr:" + getOrder();
    }
}
