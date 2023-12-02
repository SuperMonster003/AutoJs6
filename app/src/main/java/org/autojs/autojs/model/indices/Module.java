package org.autojs.autojs.model.indices;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on Dec 9, 2017.
 */
public class Module {

    @SerializedName("properties")
    private List<Property> mProperties = new ArrayList<>();

    @SerializedName("url")
    private String mUrl;
    @SerializedName("name")
    private String mName;
    @SerializedName("summary")
    private String mSummary;

    public List<Property> getProperties() {
        return mProperties;
    }

    public void setProperties(List<Property> properties) {
        mProperties = properties;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getSummary() {
        return mSummary;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public Property asGlobalProperty() {
        return new Property(mName, mUrl, mSummary, true);
    }
}
