package de.intranda.goobi.plugins.util;

import org.goobi.interfaces.IOverview;

import lombok.Data;

@Data
public class Overview implements IOverview {

    // common fields
    private String title;
    private String recordType;
    private String barcode;
    private String uri;

    //ils fields
    private String bibId;
    private String itemId;
    private String holdingId;
    private String author;
    private String callNumber;
    private String volume;
    private String pubDate;

    // Archives Space fields
    private String series;
    private String topContainer;
    private String collection;
    private String containerGrouping;
    private String aspaceUri;

}
