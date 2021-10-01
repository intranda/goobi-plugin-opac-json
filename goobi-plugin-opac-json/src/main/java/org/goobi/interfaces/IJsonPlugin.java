package org.goobi.interfaces;

import java.util.List;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

public interface IJsonPlugin extends IOpacPlugin {

    public String getWorkflowTitle();

    public void setWorkflowTitle(String value);

    public List<IOverview> getOverviewList();


    public List<ISearchField> getSearchFieldList();
}
