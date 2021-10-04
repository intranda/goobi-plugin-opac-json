package org.goobi.interfaces;

import java.util.List;
import java.util.Map;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

public interface IJsonPlugin extends IOpacPlugin {

    public String getWorkflowTitle();

    public void setWorkflowTitle(String value);

    public List<Map<String, String>> getOverviewList();


    public List<ISearchField> getSearchFieldList();

    public void setTestMode(boolean testmode);
}
