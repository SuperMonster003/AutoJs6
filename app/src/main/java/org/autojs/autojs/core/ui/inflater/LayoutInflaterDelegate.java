package org.autojs.autojs.core.ui.inflater;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import org.autojs.autojs.core.ui.inflater.inflaters.ViewGroupInflater;
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater;
import org.w3c.dom.Node;

import java.util.HashMap;

/**
 * Created by Stardust on Mar 29, 2018.
 */
public interface LayoutInflaterDelegate {

    LayoutInflaterDelegate NO_OP = new NoOp();

    View beforeInflation(InflateContext inflateContext, String xml, ViewGroup parent);

    View afterInflation(InflateContext inflateContext, View doInflation, String xml, ViewGroup parent);

    @Nullable
    String beforeConvertXml(InflateContext inflateContext, String xml);

    String afterConvertXml(InflateContext inflateContext, String xml);

    View beforeInflateView(InflateContext inflateContext, Node node, ViewGroup parent, boolean attachToParent);

    View afterInflateView(InflateContext inflateContext, View view, Node node, ViewGroup parent, boolean attachToParent);

    View beforeCreateView(InflateContext inflateContext, Node node, String viewName, ViewGroup parent);

    View afterCreateView(InflateContext inflateContext, View view, Node node, String viewName, ViewGroup parent);

    boolean beforeApplyAttributes(InflateContext inflateContext, View view, ViewInflater<View> inflater, HashMap<String, String> attrs, ViewGroup parent);

    void afterApplyAttributes(InflateContext inflateContext, View view, ViewInflater<View> inflater, HashMap<String, String> attrs, ViewGroup parent);

    boolean beforeInflateChildren(InflateContext inflateContext, ViewInflater<View> inflater, Node node, ViewGroup parent);

    void afterInflateChildren(InflateContext inflateContext, ViewInflater<View> inflater, Node node, ViewGroup parent);

    void afterApplyPendingAttributesOfChildren(InflateContext inflateContext, ViewGroupInflater inflater, ViewGroup view);

    boolean beforeApplyPendingAttributesOfChildren(InflateContext inflateContext, ViewGroupInflater inflater, ViewGroup view);

    boolean beforeApplyAttribute(InflateContext inflateContext, ViewInflater<View> inflater, View view, String ns, String attrName, String value, ViewGroup parent);

    void afterApplyAttribute(InflateContext inflateContext, ViewInflater<View> inflater, View view, String ns, String attrName, String value, ViewGroup parent);

    class NoOp implements LayoutInflaterDelegate {
        @Override
        public String beforeConvertXml(InflateContext inflateContext, String xml) {
            return null;
        }

        @Override
        public String afterConvertXml(InflateContext inflateContext, String xml) {
            return xml;
        }

        @Override
        public View afterInflation(InflateContext inflateContext, View result, String xml, ViewGroup parent) {
            return result;
        }

        @Override
        public View beforeInflation(InflateContext inflateContext, String xml, ViewGroup parent) {
            return null;
        }

        @Override
        public View beforeInflateView(InflateContext inflateContext, Node node, ViewGroup parent, boolean attachToParent) {
            return null;
        }

        @Override
        public View afterInflateView(InflateContext inflateContext, View view, Node node, ViewGroup parent, boolean attachToParent) {
            return view;
        }

        @Override
        public View beforeCreateView(InflateContext inflateContext, Node node, String viewName, ViewGroup parent) {
            return null;
        }

        @Override
        public View afterCreateView(InflateContext inflateContext, View view, Node node, String viewName, ViewGroup parent) {
            return view;
        }

        @Override
        public boolean beforeApplyAttributes(InflateContext inflateContext, View view, ViewInflater<View> inflater, HashMap<String, String> attrs, ViewGroup parent) {
            return false;
        }

        @Override
        public void afterApplyAttributes(InflateContext inflateContext, View view, ViewInflater<View> inflater, HashMap<String, String> attrs, ViewGroup parent) {
            /* Empty body. */
        }

        @Override
        public boolean beforeInflateChildren(InflateContext inflateContext, ViewInflater<View> inflater, Node node, ViewGroup parent) {
            return false;
        }

        @Override
        public void afterInflateChildren(InflateContext inflateContext, ViewInflater<View> inflater, Node node, ViewGroup parent) {
            /* Empty body. */
        }

        @Override
        public void afterApplyPendingAttributesOfChildren(InflateContext inflateContext, ViewGroupInflater inflater, ViewGroup view) {
            /* Empty body. */
        }

        @Override
        public boolean beforeApplyPendingAttributesOfChildren(InflateContext inflateContext, ViewGroupInflater inflater, ViewGroup view) {
            return false;
        }

        @Override
        public boolean beforeApplyAttribute(InflateContext inflateContext, ViewInflater<View> inflater, View view, String ns, String attrName, String value, ViewGroup parent) {
            return false;
        }

        @Override
        public void afterApplyAttribute(InflateContext inflateContext, ViewInflater<View> inflater, View view, String ns, String attrName, String value, ViewGroup parent) {
            /* Empty body. */
        }
    }
}
