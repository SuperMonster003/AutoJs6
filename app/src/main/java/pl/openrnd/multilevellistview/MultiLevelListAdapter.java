/******************************************************************************
 *
 *  2016 (C) Copyright Open-RnD Sp. z o.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package pl.openrnd.multilevellistview;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Base adapter to be used for MultiLevelListView.
 */
public abstract class MultiLevelListAdapter {

    private MultiLevelListView mView;

    private Node mRoot = new Node();
    private List<Node> mFlatItems = new ArrayList<>();
    private List<Object> mSourceData = new ArrayList<>();
    private ProxyAdapter mProxyAdapter = new ProxyAdapter();

    /**
     * Indicates if object is expandable.
     *
     * @param object The object.
     * @return true if object is expandable, false otherwise.
     */
    protected abstract boolean isExpandable(Object object);

    /**
     * Gets list of object's sub-items.
     * <p>
     * Called only for expandable objects.
     *
     * @param object The object.
     * @return List of sub-objects. Null is possible.
     */
    protected abstract List<?> getSubObjects(Object object);

    /**
     * Gets view configured to display the object.
     *
     * @param object      The object.
     * @param convertView The view that can be reused if possible. Null value if not available.
     * @param itemInfo    The InfoItem object with information about item location in MultiLevelListView.
     * @return The view that reflects the object.
     */
    protected abstract View getViewForObject(Object object, View convertView, ItemInfo itemInfo);

    /**
     * Indicates if object is expanded initially.
     * You can override this method to specify different objects' behaviour.
     * This method will NOT be called if {@link #isExpandable(Object)} returns false or {@link MultiLevelListView#isAlwaysExpanded()} returns true.
     *
     * @param object The object.
     * @return true if object is expanded, false otherwise.
     */
    protected boolean isInitiallyExpanded(Object object) {
        return false;
    }

    /**
     * Sets initial data items to be displayed in attached MultiLevelListView.
     *
     * @param dataItems The list with data items.
     */
    public void setDataItems(List<?> dataItems) {
        checkState();

        mSourceData = new ArrayList<>();
        mSourceData.addAll(dataItems);

        mRoot.setSubNodes(createNodeListFromDataItems(mSourceData, mRoot));
        notifyDataSetChanged();
    }

    /**
     * Notifies adapter that data set changed.
     */
    public void notifyDataSetChanged() {
        checkState();

        mFlatItems = createItemsForCurrentStat();
        mProxyAdapter.notifyDataSetChanged();
    }

    /**
     * Reloads data. Method is causing nodes recreation.
     */
    public void reloadData() {
        setDataItems(mSourceData);
    }

    /**
     * Throws IllegalStateException if adapter is not attached to view.
     */
    private void checkState() {
        if (mView == null) {
            throw new IllegalStateException("Adapter not connected");
        }
    }

    /**
     * Creates list of nodes for data items provided to adapter.
     *
     * @param dataItems List of objects for which nodes have to be created.
     * @param parent    Node that is a parent for nodes created for data items.
     * @return List with nodes.
     */
    private List<Node> createNodeListFromDataItems(List<?> dataItems, Node parent) {
        List<Node> result = new ArrayList<>();
        if (dataItems != null) {
            for (Object dataItem : dataItems) {
                boolean isExpandable = isExpandable(dataItem);

                Node node = new Node(dataItem, parent);
                node.setExpandable(isExpandable);
                if (isExpandable && (mView.isAlwaysExpanded() || isInitiallyExpanded(dataItem))) {
                    node.setSubNodes(createNodeListFromDataItems(getSubObjects(node.getObject()), node));
                }
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Maps current items hierarchy into flat list.
     *
     * @return Items flat list.
     */
    private List<Node> createItemsForCurrentStat() {
        List<Node> result = new ArrayList<>();
        collectItems(result, mRoot.getSubNodes());
        return result;
    }

    /**
     * Adds recurrently nodes and their sub-nodes to provided list.
     *
     * @param result Output parameter with flat list of items.
     * @param nodes  Nodes list.
     */
    private void collectItems(List<Node> result, List<Node> nodes) {
        if (nodes != null) {
            for (Node node : nodes) {
                result.add(node);
                collectItems(result, node.getSubNodes());
            }
        }
    }

    /**
     * Gets currently displayed list of items.
     *
     * @return List items.
     */
    List<Node> getFlatItems() {
        return mFlatItems;
    }

    /**
     * Unregisters adapter in MultiLevelListView.
     *
     * @param view The view to unregister.
     * @throws IllegalArgumentException if adapter is not registered in the view.
     */
    void unregisterView(MultiLevelListView view) {
        if (mView != view) {
            throw new IllegalArgumentException("Adapter not connected");
        }

        if (mView == null) {
            return;
        }

        mView.getListView().setAdapter(null);
        mView = null;
    }

    /**
     * Register adapter in MultiLevelListView.
     *
     * @param view The view to register.
     * @throws IllegalArgumentException if adapter is registered in different view.
     */
    void registerView(MultiLevelListView view) {
        if ((mView != null) && (mView != view)) {
            throw new IllegalArgumentException("Adapter already connected");
        }

        if (view == null) {
            return;
        }

        mView = view;
        mView.getListView().setAdapter(mProxyAdapter);
    }

    /**
     * Extends node.
     * <p>
     * Adds sub-nodes to the node.
     *
     * @param node    The node.
     * @param nestTyp NestType value.
     */
    void extendNode(Node node, NestType nestTyp) {
        node.setSubNodes(createNodeListFromDataItems(getSubObjects(node.getObject()), node));
        if (nestTyp == NestType.SINGLE) {
            clearPathToNode(node);
        }
        notifyDataSetChanged();
    }

    /**
     * Collapse node.
     * <p>
     * Clears node's sub-nodes.
     *
     * @param node The node
     */
    void collapseNode(Node node) {
        node.clearSubNodes();
        notifyDataSetChanged();
    }

    /**
     * Collapse any extended way not leading to the node.
     *
     * @param node The node.
     */
    private void clearPathToNode(Node node) {
        Node parent = node.getParent();
        if (parent != null) {
            List<Node> nodes = parent.getSubNodes();
            if (nodes != null) {
                for (Node sibling : nodes) {
                    if (sibling != node) {
                        sibling.clearSubNodes();
                    }
                }
            }
            clearPathToNode(parent);
        }
    }

    /**
     * Helper class used to display created flat list of item's using Android's ListView.
     */
    private class ProxyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mFlatItems == null ? 0 : mFlatItems.size();
        }

        @Override
        public Object getItem(int i) {
            return mFlatItems.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup viewGroup) {
            Node node = mFlatItems.get(i);
            return getViewForObject(node.getObject(), convertView, node.getItemInfo());
        }
    }
}
