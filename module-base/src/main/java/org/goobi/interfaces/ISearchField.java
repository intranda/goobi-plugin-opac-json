package org.goobi.interfaces;

import java.util.List;

public interface ISearchField {

    public String getId();

    public void setId(String value);

    // displayed label
    public String getLabel();

    public void setLabel(String value);

    // type of the field, implemented are text, select and select+text
    public String getType();

    public void setType(String value);

    // list of possible values
    public List<String> getSelectList();

    public void setSelectList(List<String> value);

    // value of the selected field
    public String getSelectedField();

    public void setSelectedField(String value);

    // entered text, gets filled with default value
    public String getText();

    public void setText(String value);

    public String getUrl();

    public void setUrl(String value);
}
