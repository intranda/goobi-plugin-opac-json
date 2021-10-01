package org.goobi.interfaces;

public interface IOverview {

    public String getTitle();

    public String getRecordType();

    public String getBarcode();

    public String getUri();

    public String getBibId();

    public String getItemId();

    public String getHoldingId();

    public String getAuthor();

    public String getCallNumber();

    public String getVolume();

    public String getPubDate();

    public String getSeries();

    public String getTopContainer();

    public String getCollection();

    public String getContainerGrouping();

    public String getAspaceUri();



    public void setTitle(String value);

    public void setRecordType(String value);

    public void setBarcode(String value);

    public void setUri(String value);

    public void setBibId(String value);

    public void setItemId(String value);

    public void setHoldingId(String value);

    public void setAuthor(String value);

    public void setCallNumber(String value);

    public void setVolume(String value);

    public void setPubDate(String value);

    public void setSeries(String value);

    public void setTopContainer(String value);

    public void setCollection(String value);

    public void setContainerGrouping(String value);

    public void setAspaceUri(String value);

}
