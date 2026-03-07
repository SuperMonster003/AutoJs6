package com.bignerdranch.expandablerecyclerview;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;

import com.bignerdranch.expandablerecyclerview.ParentViewHolder.ParentViewHolderExpandCollapseListener;
import com.bignerdranch.expandablerecyclerview.model.ExpandableWrapper;
import com.bignerdranch.expandablerecyclerview.model.Parent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RecyclerView.Adapter implementation that
 * adds the ability to expand and collapse list items.
 * <p>
 * Changes should be notified through:
 * {@link #notifyParentInserted(int)}
 * {@link #notifyParentRemoved(int)}
 * {@link #notifyParentChanged(int)}
 * {@link #notifyParentRangeInserted(int, int)}
 * {@link #notifyChildInserted(int, int)}
 * {@link #notifyChildRemoved(int, int)}
 * {@link #notifyChildChanged(int, int)}
 * methods and not the notify methods of RecyclerView.Adapter.
 *
 */
public abstract class ExpandableRecyclerAdapter<P extends Parent<C>, C, PVH extends ParentViewHolder, CVH extends ChildViewHolder>
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String EXPANDED_STATE_MAP = "ExpandableRecyclerAdapter.ExpandedStateMap";
    /**
     * Default ViewType for parent rows
     */
    public static final int TYPE_PARENT = 0;
    /**
     * Default ViewType for children rows
     */
    public static final int TYPE_CHILD = 1;
    /**
     * Start of user-defined view types
     */
    public static final int TYPE_FIRST_USER = 2;
    private static final int INVALID_FLAT_POSITION = -1;

    /**
     * A {@link List} of all currently expanded parents and their children, in order.
     * Changes to this list should be made through the add/remove methods
     * available in {@link ExpandableRecyclerAdapter}.
     */
    @NonNull
    protected List<ExpandableWrapper<P, C>> mFlatItemList;

    @NonNull
    private List<P> mParentList;

    @Nullable
    private ExpandCollapseListener mExpandCollapseListener;

    @NonNull
    private List<RecyclerView> mAttachedRecyclerViewPool;

    private Map<P, Boolean> mExpansionStateMap;

    /**
     * Allows objects to register themselves as expand/collapse listeners to be
     * notified of change events.
     * <p>
     * NOTE: This is not called when the expanding/collapsing is triggered programmatically via
     * {@link #expandParent} and other methods on the adapter. (Only called for changes triggered
     * from the viewholder)
     * <p>
     * Implement this in your {@link android.app.Activity} or {@link android.app.Fragment}
     * to receive these callbacks.
     */
    public interface ExpandCollapseListener {
        /**
         * Called when a parent is expanded.
         *
         * @param parentPosition The position of the parent in the list being expanded
         */
        @UiThread
        void onParentExpanded(int parentPosition);

        /**
         * Called when a parent is collapsed.
         *
         * @param parentPosition The position of the parent in the list being collapsed
         */
        @UiThread
        void onParentCollapsed(int parentPosition);
    }

    /**
     * Primary constructor. Sets up {@link #mParentList} and {@link #mFlatItemList}.
     * <p>
     * Any changes to {@link #mParentList} should be made on the original instance, and notified via
     * {@link #notifyParentInserted(int)}
     * {@link #notifyParentRemoved(int)}
     * {@link #notifyParentChanged(int)}
     * {@link #notifyParentRangeInserted(int, int)}
     * {@link #notifyChildInserted(int, int)}
     * {@link #notifyChildRemoved(int, int)}
     * {@link #notifyChildChanged(int, int)}
     * methods and not the notify methods of RecyclerView.Adapter.
     *
     * @param parentList List of all parents to be displayed in the RecyclerView that this
     *                       adapter is linked to
     */
    public ExpandableRecyclerAdapter(@NonNull List<P> parentList) {
        super();
        mParentList = parentList;
        mFlatItemList = generateFlattenedParentChildList(parentList);
        mAttachedRecyclerViewPool = new ArrayList<>();
        mExpansionStateMap = new HashMap<>(mParentList.size());
    }

    /**
     * Implementation of Adapter.onCreateViewHolder(ViewGroup, int)
     * that determines if the list item is a parent or a child and calls through
     * to the appropriate implementation of either {@link #onCreateParentViewHolder(ViewGroup, int)}
     * or {@link #onCreateChildViewHolder(ViewGroup, int)}.
     *
     * @param viewGroup The {@link ViewGroup} into which the new {@link android.view.View}
     *                  will be added after it is bound to an adapter position.
     * @param viewType  The view type of the new {@code android.view.View}.
     * @return A new RecyclerView.ViewHolder
     * that holds a {@code android.view.View} of the given view type.
     */
    @NonNull
    @Override
    @UiThread
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        if (isParentViewType(viewType)) {
            PVH pvh = onCreateParentViewHolder(viewGroup, viewType);
            pvh.setParentViewHolderExpandCollapseListener(mParentViewHolderExpandCollapseListener);
            pvh.mExpandableAdapter = this;
            return pvh;
        } else {
            CVH cvh = onCreateChildViewHolder(viewGroup, viewType);
            cvh.mExpandableAdapter = this;
            return cvh;
        }
    }

    /**
     * Implementation of Adapter.onBindViewHolder(RecyclerView.ViewHolder, int)
     * that determines if the list item is a parent or a child and calls through
     * to the appropriate implementation of either
     * {@link #onBindParentViewHolder(ParentViewHolder, int, Parent)} or
     * {@link #onBindChildViewHolder(ChildViewHolder, int, int, Object)}.
     *
     * @param holder The RecyclerView.ViewHolder to bind data to
     * @param flatPosition The index in the merged list of children and parents at which to bind
     */
    @Override
    @SuppressWarnings("unchecked")
    @UiThread
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int flatPosition) {
        if (flatPosition > mFlatItemList.size()) {
            throw new IllegalStateException("Trying to bind item out of bounds, size " + mFlatItemList.size()
                    + " flatPosition " + flatPosition + ". Was the data changed without a call to notify...()?");
        }

        ExpandableWrapper<P, C> listItem = mFlatItemList.get(flatPosition);
        if (listItem.isParent()) {
            PVH parentViewHolder = (PVH) holder;

            if (parentViewHolder.shouldItemViewClickToggleExpansion()) {
                parentViewHolder.setMainItemClickToExpand();
            }

            parentViewHolder.setExpanded(listItem.isExpanded());
            parentViewHolder.mParent = listItem.getParent();
            onBindParentViewHolder(parentViewHolder, getNearestParentPosition(flatPosition), listItem.getParent());
        } else {
            CVH childViewHolder = (CVH) holder;
            childViewHolder.mChild = listItem.getChild();
            onBindChildViewHolder(childViewHolder, getNearestParentPosition(flatPosition), getChildPosition(flatPosition), listItem.getChild());
        }
    }

    /**
     * Callback called from {@link #onCreateViewHolder(ViewGroup, int)} when
     * the list item created is a parent.
     *
     * @param parentViewGroup The {@link ViewGroup} in the list for which a {@link PVH} is being
     *                        created
     * @return A {@code PVH} corresponding to the parent with the {@code ViewGroup} parentViewGroup
     */
    @NonNull
    @UiThread
    public abstract PVH onCreateParentViewHolder(@NonNull ViewGroup parentViewGroup, int viewType);

    /**
     * Callback called from {@link #onCreateViewHolder(ViewGroup, int)} when
     * the list item created is a child.
     *
     * @param childViewGroup The {@link ViewGroup} in the list for which a {@link CVH}
     *                       is being created
     * @return A {@code CVH} corresponding to the child with the {@code ViewGroup} childViewGroup
     */
    @NonNull
    @UiThread
    public abstract CVH onCreateChildViewHolder(@NonNull ViewGroup childViewGroup, int viewType);

    /**
     * Callback called from onBindViewHolder(RecyclerView.ViewHolder, int)
     * when the list item bound to is a parent.
     * <p>
     * Bind data to the {@link PVH} here.
     *
     * @param parentViewHolder The {@code PVH} to bind data to
     * @param parentPosition The position of the parent to bind
     * @param parent The parent which holds the data to be bound to the {@code PVH}
     */
    @UiThread
    public abstract void onBindParentViewHolder(@NonNull PVH parentViewHolder, int parentPosition, @NonNull P parent);

    /**
     * Callback called from onBindViewHolder(RecyclerView.ViewHolder, int)
     * when the list item bound to is a child.
     * <p>
     * Bind data to the {@link CVH} here.
     *
     * @param childViewHolder The {@code CVH} to bind data to
     * @param parentPosition The position of the parent that contains the child to bind
     * @param childPosition The position of the child to bind
     * @param child The child which holds that data to be bound to the {@code CVH}
     */
    @UiThread
    public abstract void onBindChildViewHolder(@NonNull CVH childViewHolder, int parentPosition, int childPosition, @NonNull C child);

    /**
     * Gets the number of parents and children currently expanded.
     *
     * @return The size of {@link #mFlatItemList}
     */
    @Override
    @UiThread
    public int getItemCount() {
        return mFlatItemList.size();
    }

    /**
     * For multiple view type support look at overriding {@link #getParentViewType(int)} and
     * {@link #getChildViewType(int, int)}. Almost all cases should override those instead
     * of this method.
     *
     * @param flatPosition The index in the merged list of children and parents to get the view type of
     * @return Gets the view type of the item at the given flatPosition.
     */
    @Override
    @UiThread
    public int getItemViewType(int flatPosition) {
        ExpandableWrapper<P, C> listItem = mFlatItemList.get(flatPosition);
        if (listItem.isParent()) {
            return getParentViewType(getNearestParentPosition(flatPosition));
        } else {
            return getChildViewType(getNearestParentPosition(flatPosition), getChildPosition(flatPosition));
        }
    }

    /**
     * Return the view type of the parent at {@code parentPosition} for the purposes of view recycling.
     * <p>
     * The default implementation of this method returns {@link #TYPE_PARENT}, making the assumption of
     * a single view type for the parents in this adapter. Unlike ListView adapters, types need not
     * be contiguous. Consider using id resources to uniquely identify item view types.
     * <p>
     * If you are overriding this method make sure to override {@link #isParentViewType(int)} as well.
     * <p>
     * Start your defined viewtypes at {@link #TYPE_FIRST_USER}
     *
     * @param parentPosition The index of the parent to query
     * @return integer value identifying the type of the view needed to represent the parent at
     *                 {@code parentPosition}. Type codes need not be contiguous.
     */
    public int getParentViewType(int parentPosition) {
        return TYPE_PARENT;
    }


    /**
     * Return the view type of the child {@code parentPosition} contained within the parent
     * at {@code parentPosition} for the purposes of view recycling.
     * <p>
     * The default implementation of this method returns {@link #TYPE_CHILD}, making the assumption of
     * a single view type for the children in this adapter. Unlike ListView adapters, types need not
     * be contiguous. Consider using id resources to uniquely identify item view types.
     * <p>
     * Start your defined viewtypes at {@link #TYPE_FIRST_USER}
     *
     * @param parentPosition The index of the parent continaing the child to query
     * @param childPosition The index of the child within the parent to query
     * @return integer value identifying the type of the view needed to represent the child at
     *                 {@code parentPosition}. Type codes need not be contiguous.
     */
    public int getChildViewType(int parentPosition, int childPosition) {
        return TYPE_CHILD;
    }

    /**
     * Used to determine whether a viewType is that of a parent or not, for ViewHolder creation purposes.
     * <p>
     * Only override if {@link #getParentViewType(int)} is being overriden
     *
     * @param viewType the viewType identifier in question
     * @return whether the given viewType belongs to a parent view
     */
    public boolean isParentViewType(int viewType) {
        return viewType == TYPE_PARENT;
    }

    /**
     * Gets the list of parents that is backing this adapter.
     * Changes can be made to the list and the adapter notified via the
     * {@link #notifyParentInserted(int)}
     * {@link #notifyParentRemoved(int)}
     * {@link #notifyParentChanged(int)}
     * {@link #notifyParentRangeInserted(int, int)}
     * {@link #notifyChildInserted(int, int)}
     * {@link #notifyChildRemoved(int, int)}
     * {@link #notifyChildChanged(int, int)}
     * methods.
     *
     *
     * @return The list of parents that this adapter represents
     */
    @NonNull
    @UiThread
    public List<P> getParentList() {
        return mParentList;
    }

    /**
     * Set a new list of parents and notify any registered observers that the data set has changed.
     * <p>
     * This setter does not specify what about the data set has changed, forcing
     * any observers to assume that all existing items and structure may no longer be valid.
     * LayoutManagers will be forced to fully rebind and relayout all visible views.</p>
     * <p>
     * It will always be more efficient to use the more specific change events if you can.
     * Rely on {@code #setParentList(List, boolean)} as a last resort. There will be no animation
     * of changes, unlike the more specific change events listed below.
     *
     * @see #notifyParentInserted(int)
     * @see #notifyParentRemoved(int)
     * @see #notifyParentChanged(int)
     * @see #notifyParentRangeInserted(int, int)
     * @see #notifyChildInserted(int, int)
     * @see #notifyChildRemoved(int, int)
     * @see #notifyChildChanged(int, int)
     *
     * @param preserveExpansionState If true, the adapter will attempt to preserve your parent's last expanded
     *                               state. This depends on object equality for comparisons of
     *                               old parents to parents in the new list.
     *
     *                               If false, only {@link Parent#isInitiallyExpanded()}
     *                               will be used to determine expanded state.
     *
     */
    @UiThread
    public void setParentList(@NonNull List<P> parentList, boolean preserveExpansionState) {
        mParentList = parentList;
        notifyParentDataSetChanged(preserveExpansionState);
    }

    /**
     * Implementation of Adapter#onAttachedToRecyclerView(RecyclerView).
     * <p>
     * Called when this {@link ExpandableRecyclerAdapter} is attached to a RecyclerView.
     *
     * @param recyclerView The {@code RecyclerView} this {@code ExpandableRecyclerAdapter}
     *                     is being attached to
     */
    @Override
    @UiThread
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mAttachedRecyclerViewPool.add(recyclerView);
    }


    /**
     * Implementation of Adapter.onDetachedFromRecyclerView(RecyclerView)
     * <p>
     * Called when this ExpandableRecyclerAdapter is detached from a RecyclerView.
     *
     * @param recyclerView The {@code RecyclerView} this {@code ExpandableRecyclerAdapter}
     *                     is being detached from
     */
    @Override
    @UiThread
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        mAttachedRecyclerViewPool.remove(recyclerView);
    }

    @UiThread
    public void setExpandCollapseListener(@Nullable ExpandCollapseListener expandCollapseListener) {
        mExpandCollapseListener = expandCollapseListener;
    }

    /**
     * Called when a ParentViewHolder has triggered an expansion for it's parent
     *
     * @param flatParentPosition the position of the parent that is calling to be expanded
     */
    @UiThread
    protected void parentExpandedFromViewHolder(int flatParentPosition) {
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        updateExpandedParent(parentWrapper, flatParentPosition, true);
    }

    /**
     * Called when a ParentViewHolder has triggered a collapse for it's parent
     *
     * @param flatParentPosition the position of the parent that is calling to be collapsed
     */
    @UiThread
    protected void parentCollapsedFromViewHolder(int flatParentPosition) {
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        updateCollapsedParent(parentWrapper, flatParentPosition, true);
    }

    private ParentViewHolderExpandCollapseListener mParentViewHolderExpandCollapseListener = new ParentViewHolderExpandCollapseListener() {

        /**
         * Implementation of {@link ParentViewHolderExpandCollapseListener#onParentExpanded(int)}.
         * <p>
         * Called when a {@link P} is triggered to expand.
         *
         * @param flatParentPosition The index of the item in the list being expanded, relative to the flattened list
         */
        @Override
        @UiThread
        public void onParentExpanded(int flatParentPosition) {
            parentExpandedFromViewHolder(flatParentPosition);
        }

        /**
         * Implementation of {@link ParentViewHolderExpandCollapseListener#onParentCollapsed(int)}.
         * <p>
         * Called when a {@link P} is triggered to collapse.
         *
         * @param flatParentPosition The index of the item in the list being collapsed, relative to the flattened list
         */
        @Override
        @UiThread
        public void onParentCollapsed(int flatParentPosition) {
            parentCollapsedFromViewHolder(flatParentPosition);
        }
    };

    // region Programmatic Expansion/Collapsing

    /**
     * Expands the parent associated with a specified {@link P} in the list of parents.
     *
     * @param parent The {@code P} of the parent to expand
     */
    @UiThread
    public void expandParent(@NonNull P parent) {
        ExpandableWrapper<P, C> parentWrapper = new ExpandableWrapper<>(parent);
        int flatParentPosition = mFlatItemList.indexOf(parentWrapper);
        if (flatParentPosition == INVALID_FLAT_POSITION) {
            return;
        }

        expandViews(mFlatItemList.get(flatParentPosition), flatParentPosition);
    }

    /**
     * Expands the parent with the specified index in the list of parents.
     *
     * @param parentPosition The position of the parent to expand
     */
    @UiThread
    public void expandParent(int parentPosition) {
        expandParent(mParentList.get(parentPosition));
    }

    /**
     * Expands all parents in a range of indices in the list of parents.
     *
     * @param startParentPosition The index at which to to start expanding parents
     * @param parentCount The number of parents to expand
     */
    @UiThread
    public void expandParentRange(int startParentPosition, int parentCount) {
        int endParentPosition = startParentPosition + parentCount;
        for (int i = startParentPosition; i < endParentPosition; i++) {
            expandParent(i);
        }
    }

    /**
     * Expands all parents in the list.
     */
    @UiThread
    public void expandAllParents() {
        for (P parent : mParentList) {
            expandParent(parent);
        }
    }

    /**
     * Collapses the parent associated with a specified {@link P} in the list of parents.
     *
     * @param parent The {@code P} of the parent to collapse
     */
    @UiThread
    public void collapseParent(@NonNull P parent) {
        ExpandableWrapper<P, C> parentWrapper = new ExpandableWrapper<>(parent);
        int flatParentPosition = mFlatItemList.indexOf(parentWrapper);
        if (flatParentPosition == INVALID_FLAT_POSITION) {
            return;
        }

        collapseViews(mFlatItemList.get(flatParentPosition), flatParentPosition);
    }

    /**
     * Collapses the parent with the specified index in the list of parents.
     *
     * @param parentPosition The index of the parent to collapse
     */
    @UiThread
    public void collapseParent(int parentPosition) {
        collapseParent(mParentList.get(parentPosition));
    }

    /**
     * Collapses all parents in a range of indices in the list of parents.
     *
     * @param startParentPosition The index at which to to start collapsing parents
     * @param parentCount The number of parents to collapse
     */
    @UiThread
    public void collapseParentRange(int startParentPosition, int parentCount) {
        int endParentPosition = startParentPosition + parentCount;
        for (int i = startParentPosition; i < endParentPosition; i++) {
            collapseParent(i);
        }
    }

    /**
     * Collapses all parents in the list.
     */
    @UiThread
    public void collapseAllParents() {
        for (P parent : mParentList) {
            collapseParent(parent);
        }
    }

    /**
     * Stores the expanded state map across state loss.
     * <p>
     * Should be called from {@link Activity#onSaveInstanceState(Bundle)} in
     * the {@link Activity} that hosts the RecyclerView that this
     * {@link ExpandableRecyclerAdapter} is attached to.
     * <p>
     * This will make sure to add the expanded state map as an extra to the
     * instance state bundle to be used in {@link #onRestoreInstanceState(Bundle)}.
     *
     * @param savedInstanceState The {@code Bundle} into which to store the
     *                           expanded state map
     */
    @UiThread
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        savedInstanceState.putSerializable(EXPANDED_STATE_MAP, generateExpandedStateMap());
    }

    /**
     * Fetches the expandable state map from the saved instance state {@link Bundle}
     * and restores the expanded states of all of the parents.
     * <p>
     * Should be called from {@link Activity#onRestoreInstanceState(Bundle)} in
     * the {@link Activity} that hosts the RecyclerView that this
     * {@link ExpandableRecyclerAdapter} is attached to.
     * <p>
     * Assumes that the list of parents is the same as when the saved
     * instance state was stored.
     *
     * @param savedInstanceState The {@code Bundle} from which the expanded
     *                           state map is loaded
     */
    @SuppressWarnings("unchecked")
    @UiThread
    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null
                || !savedInstanceState.containsKey(EXPANDED_STATE_MAP)) {
            return;
        }

        HashMap<Integer, Boolean> expandedStateMap = (HashMap<Integer, Boolean>) savedInstanceState.getSerializable(EXPANDED_STATE_MAP);
        if (expandedStateMap == null) {
            return;
        }

        List<ExpandableWrapper<P, C>> itemList = new ArrayList<>();
        int parentsCount = mParentList.size();
        for (int i = 0; i < parentsCount; i++) {
            ExpandableWrapper<P, C> parentWrapper = new ExpandableWrapper<>(mParentList.get(i));
            itemList.add(parentWrapper);

            if (expandedStateMap.containsKey(i)) {
                boolean expanded = expandedStateMap.get(i);
                parentWrapper.setExpanded(expanded);

                if (expanded) {
                    List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
                    int childrenCount = wrappedChildList.size();
                    for (int j = 0; j < childrenCount; j++) {
                        ExpandableWrapper<P, C> childWrapper = wrappedChildList.get(j);
                        itemList.add(childWrapper);
                    }
                }
            }
        }

        mFlatItemList = itemList;

        notifyDataSetChanged();
    }

    /**
     * Calls through to the ParentViewHolder to expand views for each
     * RecyclerView the specified parent is a child of.
     * <p>
     * These calls to the ParentViewHolder are made so that animations can be
     * triggered at the ViewHolder level.
     *
     * @param flatParentPosition The index of the parent to expand
     */
    @SuppressWarnings("unchecked")
    @UiThread
    private void expandViews(@NonNull ExpandableWrapper<P, C> parentWrapper, int flatParentPosition) {
        PVH viewHolder;
        for (RecyclerView recyclerView : mAttachedRecyclerViewPool) {
            viewHolder = (PVH) recyclerView.findViewHolderForAdapterPosition(flatParentPosition);
            if (viewHolder != null && !viewHolder.isExpanded()) {
                viewHolder.setExpanded(true);
                viewHolder.onExpansionToggled(false);
            }
        }

        updateExpandedParent(parentWrapper, flatParentPosition, false);
    }

    /**
     * Calls through to the ParentViewHolder to collapse views for each
     * RecyclerView a specified parent is a child of.
     * <p>
     * These calls to the ParentViewHolder are made so that animations can be
     * triggered at the ViewHolder level.
     *
     * @param flatParentPosition The index of the parent to collapse
     */
    @SuppressWarnings("unchecked")
    @UiThread
    private void collapseViews(@NonNull ExpandableWrapper<P, C> parentWrapper, int flatParentPosition) {
        PVH viewHolder;
        for (RecyclerView recyclerView : mAttachedRecyclerViewPool) {
            viewHolder = (PVH) recyclerView.findViewHolderForAdapterPosition(flatParentPosition);
            if (viewHolder != null && viewHolder.isExpanded()) {
                viewHolder.setExpanded(false);
                viewHolder.onExpansionToggled(true);
            }
        }

        updateCollapsedParent(parentWrapper, flatParentPosition, false);
    }

    /**
     * Expands a specified parent. Calls through to the
     * ExpandCollapseListener and adds children of the specified parent to the
     * flat list of items.
     *
     * @param parentWrapper The ExpandableWrapper of the parent to expand
     * @param flatParentPosition The index of the parent to expand
     * @param expansionTriggeredByListItemClick true if expansion was triggered
     *                                          by a click event, false otherwise.
     */
    @UiThread
    private void updateExpandedParent(@NonNull ExpandableWrapper<P, C> parentWrapper, int flatParentPosition, boolean expansionTriggeredByListItemClick) {
        if (parentWrapper.isExpanded()) {
            return;
        }

        parentWrapper.setExpanded(true);
        mExpansionStateMap.put(parentWrapper.getParent(), true);

        List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
        if (wrappedChildList != null) {
            int childCount = wrappedChildList.size();
            for (int i = 0; i < childCount; i++) {
                mFlatItemList.add(flatParentPosition + i + 1, wrappedChildList.get(i));
            }

            notifyItemRangeInserted(flatParentPosition + 1, childCount);
        }

        if (expansionTriggeredByListItemClick && mExpandCollapseListener != null) {
            mExpandCollapseListener.onParentExpanded(getNearestParentPosition(flatParentPosition));
        }
    }

    /**
     * Collapses a specified parent item. Calls through to the
     * ExpandCollapseListener and removes children of the specified parent from the
     * flat list of items.
     *
     * @param parentWrapper The ExpandableWrapper of the parent to collapse
     * @param flatParentPosition The index of the parent to collapse
     * @param collapseTriggeredByListItemClick true if expansion was triggered
     *                                         by a click event, false otherwise.
     */
    @UiThread
    private void updateCollapsedParent(@NonNull ExpandableWrapper<P, C> parentWrapper, int flatParentPosition, boolean collapseTriggeredByListItemClick) {
        if (!parentWrapper.isExpanded()) {
            return;
        }

        parentWrapper.setExpanded(false);
        mExpansionStateMap.put(parentWrapper.getParent(), false);

        List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
        if (wrappedChildList != null) {
            int childCount = wrappedChildList.size();
            for (int i = childCount - 1; i >= 0; i--) {
                mFlatItemList.remove(flatParentPosition + i + 1);
            }

            notifyItemRangeRemoved(flatParentPosition + 1, childCount);
        }

        if (collapseTriggeredByListItemClick && mExpandCollapseListener != null) {
            mExpandCollapseListener.onParentCollapsed(getNearestParentPosition(flatParentPosition));
        }
    }

    /**
     * Given the index relative to the entire RecyclerView, returns the nearest
     * ParentPosition without going past the given index.
     * <p>
     * If it is the index of a parent, will return the corresponding parent position.
     * If it is the index of a child within the RV, will return the position of that child's parent.
     */
    @UiThread
    int getNearestParentPosition(int flatPosition) {
        if (flatPosition == 0) {
            return 0;
        }

        int parentCount = -1;
        for (int i = 0; i <= flatPosition; i++) {
            ExpandableWrapper<P, C> listItem = mFlatItemList.get(i);
            if (listItem.isParent()) {
                parentCount++;
            }
        }
        return parentCount;
    }

    /**
     * Given the index relative to the entire RecyclerView for a child item,
     * returns the child position within the child list of the parent.
     */
    @UiThread
    int getChildPosition(int flatPosition) {
        if (flatPosition == 0) {
            return 0;
        }

        int childCount = 0;
        for (int i = 0; i < flatPosition; i++) {
            ExpandableWrapper<P, C> listItem = mFlatItemList.get(i);
            if (listItem.isParent()) {
                childCount = 0;
            } else {
                childCount++;
            }
        }
        return childCount;
    }

    // endregion

    // region Data Manipulation

    /**
     * Notify any registered observers that the data set has changed.
     * <p>
     * This event does not specify what about the data set has changed, forcing
     * any observers to assume that all existing items and structure may no longer be valid.
     * LayoutManagers will be forced to fully rebind and relayout all visible views.</p>
     * <p>
     * It will always be more efficient to use the more specific change events if you can.
     * Rely on {@code #notifyParentDataSetChanged(boolean)} as a last resort. There will be no animation
     * of changes, unlike the more specific change events listed below.
     *
     * @see #notifyParentInserted(int)
     * @see #notifyParentRemoved(int)
     * @see #notifyParentChanged(int)
     * @see #notifyParentRangeInserted(int, int)
     * @see #notifyChildInserted(int, int)
     * @see #notifyChildRemoved(int, int)
     * @see #notifyChildChanged(int, int)
     *
     * @param preserveExpansionState If true, the adapter will attempt to preserve your parent's last expanded
     *                               state. This depends on object equality for comparisons of
     *                               old parents to parents in the new list.
     *
     *                               If false, only {@link Parent#isInitiallyExpanded()}
     *                               will be used to determine expanded state.
     *
     */
    @UiThread
    public void notifyParentDataSetChanged(boolean preserveExpansionState) {
        if (preserveExpansionState) {
            mFlatItemList = generateFlattenedParentChildList(mParentList, mExpansionStateMap);
        } else {
            mFlatItemList = generateFlattenedParentChildList(mParentList);
        }
        notifyDataSetChanged();
    }

    /**
     * Notify any registered observers that the parent reflected at {@code parentPosition}
     * has been newly inserted. The parent previously at {@code parentPosition} is now at
     * position {@code parentPosition + 1}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.
     *
     * @param parentPosition Position of the newly inserted parent in the data set, relative
     *                       to the list of parents only.
     *
     * @see #notifyParentRangeInserted(int, int)
     */
    @UiThread
    public void notifyParentInserted(int parentPosition) {
        P parent = mParentList.get(parentPosition);

        int flatParentPosition;
        if (parentPosition < mParentList.size() - 1) {
            flatParentPosition = getFlatParentPosition(parentPosition);
        } else {
            flatParentPosition = mFlatItemList.size();
        }

        int sizeChanged = addParentWrapper(flatParentPosition, parent);
        notifyItemRangeInserted(flatParentPosition, sizeChanged);
    }

    /**
     * Notify any registered observers that the currently reflected {@code itemCount}
     * parents starting at {@code parentPositionStart} have been newly inserted.
     * The parents previously located at {@code parentPositionStart} and beyond
     * can now be found starting at position {@code parentPositionStart + itemCount}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.
     *
     * @param parentPositionStart Position of the first parent that was inserted, relative
     *                            to the list of parents only.
     * @param itemCount Number of items inserted
     *
     * @see #notifyParentInserted(int)
     */
    @UiThread
    public void notifyParentRangeInserted(int parentPositionStart, int itemCount) {
        int initialFlatParentPosition;
        if (parentPositionStart < mParentList.size() - itemCount) {
            initialFlatParentPosition = getFlatParentPosition(parentPositionStart);
        } else {
            initialFlatParentPosition = mFlatItemList.size();
        }

        int sizeChanged = 0;
        int flatParentPosition = initialFlatParentPosition;
        int changed;
        int parentPositionEnd = parentPositionStart + itemCount;
        for (int i = parentPositionStart; i < parentPositionEnd; i++) {
            P parent = mParentList.get(i);
            changed = addParentWrapper(flatParentPosition, parent);
            flatParentPosition += changed;
            sizeChanged += changed;
        }

        notifyItemRangeInserted(initialFlatParentPosition, sizeChanged);
    }

    @UiThread
    private int addParentWrapper(int flatParentPosition, P parent) {
        int sizeChanged = 1;
        ExpandableWrapper<P, C> parentWrapper = new ExpandableWrapper<>(parent);
        mFlatItemList.add(flatParentPosition, parentWrapper);
        if (parentWrapper.isParentInitiallyExpanded()) {
            parentWrapper.setExpanded(true);
            List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
            mFlatItemList.addAll(flatParentPosition + sizeChanged, wrappedChildList);
            sizeChanged += wrappedChildList.size();
        }
        return sizeChanged;
    }

    /**
     * Notify any registered observers that the parents previously located at {@code parentPosition}
     * has been removed from the data set. The parents previously located at and after
     * {@code parentPosition} may now be found at {@code oldPosition - 1}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.
     *
     * @param parentPosition Position of the parent that has now been removed, relative
     *                       to the list of parents only.
     */
    @UiThread
    public void notifyParentRemoved(int parentPosition) {
        int flatParentPosition = getFlatParentPosition(parentPosition);
        int sizeChanged = removeParentWrapper(flatParentPosition);

        notifyItemRangeRemoved(flatParentPosition, sizeChanged);
    }

    /**
     * Notify any registered observers that the {@code itemCount} parents previously
     * located at {@code parentPositionStart} have been removed from the data set. The parents
     * previously located at and after {@code parentPositionStart + itemCount} may now be
     * found at {@code oldPosition - itemCount}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.
     *
     * @param parentPositionStart The previous position of the first parent that was
     *                            removed, relative to list of parents only.
     * @param itemCount Number of parents removed from the data set
     */
    public void notifyParentRangeRemoved(int parentPositionStart, int itemCount) {
        int sizeChanged = 0;
        int flatParentPositionStart = getFlatParentPosition(parentPositionStart);
        for (int i = 0; i < itemCount; i++) {
            sizeChanged += removeParentWrapper(flatParentPositionStart);
        }

        notifyItemRangeRemoved(flatParentPositionStart, sizeChanged);
    }

    @UiThread
    private int removeParentWrapper(int flatParentPosition) {
        int sizeChanged = 1;
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.remove(flatParentPosition);
        if (parentWrapper.isExpanded()) {
            int childListSize = parentWrapper.getWrappedChildList().size();
            for (int i = 0; i < childListSize; i++) {
                mFlatItemList.remove(flatParentPosition);
                sizeChanged++;
            }
        }
        return sizeChanged;
    }

    /**
     * Notify any registered observers that the parent at {@code parentPosition} has changed.
     * This will also trigger an item changed for children of the parent list specified.
     * <p>
     * This is an item change event, not a structural change event. It indicates that any
     * reflection of the data at {@code parentPosition} is out of date and should be updated.
     * The parent at {@code parentPosition} retains the same identity. This means
     * the number of children must stay the same.
     *
     * @param parentPosition Position of the item that has changed
     */
    @UiThread
    public void notifyParentChanged(int parentPosition) {
        P parent = mParentList.get(parentPosition);
        int flatParentPositionStart = getFlatParentPosition(parentPosition);
        int sizeChanged = changeParentWrapper(flatParentPositionStart, parent);

        notifyItemRangeChanged(flatParentPositionStart, sizeChanged);
    }

    /**
     * Notify any registered observers that the {@code itemCount} parents starting
     * at {@code parentPositionStart} have changed. This will also trigger an item changed
     * for children of the parent list specified.
     * <p>
     * This is an item change event, not a structural change event. It indicates that any
     * reflection of the data in the given position range is out of date and should be updated.
     * The parents in the given range retain the same identity. This means that the number of
     * children must stay the same.
     *
     * @param parentPositionStart Position of the item that has changed
     * @param itemCount Number of parents changed in the data set
     */
    @UiThread
    public void notifyParentRangeChanged(int parentPositionStart, int itemCount) {
        int flatParentPositionStart = getFlatParentPosition(parentPositionStart);

        int flatParentPosition = flatParentPositionStart;
        int sizeChanged = 0;
        int changed;
        P parent;
        for (int j = 0; j < itemCount; j++) {
            parent = mParentList.get(parentPositionStart);
            changed = changeParentWrapper(flatParentPosition, parent);
            sizeChanged += changed;
            flatParentPosition += changed;
            parentPositionStart++;
        }
        notifyItemRangeChanged(flatParentPositionStart, sizeChanged);
    }

    private int changeParentWrapper(int flatParentPosition, P parent) {
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        parentWrapper.setParent(parent);
        int sizeChanged = 1;
        if (parentWrapper.isExpanded()) {
            List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
            int childSize = wrappedChildList.size();
            for (int i = 0; i < childSize; i++) {
                mFlatItemList.set(flatParentPosition + i + 1, wrappedChildList.get(i));
                sizeChanged++;
            }
        }

        return sizeChanged;
    }

    /**
     * Notify any registered observers that the parent and its children reflected at
     * {@code fromParentPosition} has been moved to {@code toParentPosition}.
     * <p>
     * <p>This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.</p>
     *
     * @param fromParentPosition Previous position of the parent, relative to the list of
     *                           parents only.
     * @param toParentPosition New position of the parent, relative to the list of parents only.
     */
    @UiThread
    public void notifyParentMoved(int fromParentPosition, int toParentPosition) {
        int fromFlatParentPosition = getFlatParentPosition(fromParentPosition);
        ExpandableWrapper<P, C> fromParentWrapper = mFlatItemList.get(fromFlatParentPosition);

        // If the parent is collapsed we can take advantage of notifyItemMoved otherwise
        // we are forced to do a "manual" move by removing and then adding the parent + children
        // (no notifyItemRangeMovedAvailable)
        boolean isCollapsed = !fromParentWrapper.isExpanded();
        boolean isExpandedNoChildren = !isCollapsed && (fromParentWrapper.getWrappedChildList().size() == 0);
        if (isCollapsed || isExpandedNoChildren) {
            int toFlatParentPosition = getFlatParentPosition(toParentPosition);
            ExpandableWrapper<P, C> toParentWrapper = mFlatItemList.get(toFlatParentPosition);
            mFlatItemList.remove(fromFlatParentPosition);
            int childOffset = 0;
            if (toParentWrapper.isExpanded()) {
                childOffset = toParentWrapper.getWrappedChildList().size();
            }
            mFlatItemList.add(toFlatParentPosition + childOffset, fromParentWrapper);

            notifyItemMoved(fromFlatParentPosition, toFlatParentPosition + childOffset);
        } else {
            // Remove the parent and children
            int sizeChanged = 0;
            int childListSize = fromParentWrapper.getWrappedChildList().size();
            for (int i = 0; i < childListSize + 1; i++) {
                mFlatItemList.remove(fromFlatParentPosition);
                sizeChanged++;
            }
            notifyItemRangeRemoved(fromFlatParentPosition, sizeChanged);


            // Add the parent and children at new position
            int toFlatParentPosition = getFlatParentPosition(toParentPosition);
            int childOffset = 0;
            if (toFlatParentPosition != INVALID_FLAT_POSITION) {
                ExpandableWrapper<P, C> toParentWrapper = mFlatItemList.get(toFlatParentPosition);
                if (toParentWrapper.isExpanded()) {
                    childOffset = toParentWrapper.getWrappedChildList().size();
                }
            } else {
                toFlatParentPosition = mFlatItemList.size();
            }

            mFlatItemList.add(toFlatParentPosition + childOffset, fromParentWrapper);
            List<ExpandableWrapper<P, C>> wrappedChildList = fromParentWrapper.getWrappedChildList();
            sizeChanged = wrappedChildList.size() + 1;

            mFlatItemList.addAll(toFlatParentPosition + childOffset + 1, wrappedChildList);

            notifyItemRangeInserted(toFlatParentPosition + childOffset, sizeChanged);
        }
    }

    /**
     * Notify any registered observers that the parent reflected at {@code parentPosition}
     * has a child list item that has been newly inserted at {@code childPosition}.
     * The child list item previously at {@code childPosition} is now at
     * position {@code childPosition + 1}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.
     *
     * @param parentPosition Position of the parent which has been added a child, relative
     *                       to the list of parents only.
     * @param childPosition Position of the child that has been inserted, relative to children
     *                      of the parent specified by {@code parentPosition} only.
     *
     */
    @UiThread
    public void notifyChildInserted(int parentPosition, int childPosition) {
        int flatParentPosition = getFlatParentPosition(parentPosition);
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);

        parentWrapper.setParent(mParentList.get(parentPosition));
        if (parentWrapper.isExpanded()) {
            ExpandableWrapper<P, C> child = parentWrapper.getWrappedChildList().get(childPosition);
            mFlatItemList.add(flatParentPosition + childPosition + 1, child);
            notifyItemInserted(flatParentPosition + childPosition + 1);
        }
    }

    /**
     * Notify any registered observers that the parent reflected at {@code parentPosition}
     * has {@code itemCount} child list items that have been newly inserted at {@code childPositionStart}.
     * The child list item previously at {@code childPositionStart} and beyond are now at
     * position {@code childPositionStart + itemCount}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.
     *
     * @param parentPosition Position of the parent which has been added a child, relative
     *                       to the list of parents only.
     * @param childPositionStart Position of the first child that has been inserted,
     *                           relative to children of the parent specified by
     *                           {@code parentPosition} only.
     * @param itemCount          number of children inserted
     */
    @UiThread
    public void notifyChildRangeInserted(int parentPosition, int childPositionStart, int itemCount) {
        int flatParentPosition = getFlatParentPosition(parentPosition);
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);

        parentWrapper.setParent(mParentList.get(parentPosition));
        if (parentWrapper.isExpanded()) {
            List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
            for (int i = 0; i < itemCount; i++) {
                ExpandableWrapper<P, C> child = wrappedChildList.get(childPositionStart + i);
                mFlatItemList.add(flatParentPosition + childPositionStart + i + 1, child);
            }
            notifyItemRangeInserted(flatParentPosition + childPositionStart + 1, itemCount);
        }
    }

    /**
     * Notify any registered observers that the parent located at {@code parentPosition}
     * has a child that has been removed from the data set, previously located at {@code childPosition}.
     * The child list item previously located at and after {@code childPosition} may
     * now be found at {@code childPosition - 1}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.
     *
     * @param parentPosition Position of the parent which has a child removed from, relative
     *                       to the list of parents only.
     * @param childPosition Position of the child that has been removed, relative to children
     *                      of the parent specified by {@code parentPosition} only.
     */
    @UiThread
    public void notifyChildRemoved(int parentPosition, int childPosition) {
        int flatParentPosition = getFlatParentPosition(parentPosition);
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        parentWrapper.setParent(mParentList.get(parentPosition));

        if (parentWrapper.isExpanded()) {
            mFlatItemList.remove(flatParentPosition + childPosition + 1);
            notifyItemRemoved(flatParentPosition + childPosition + 1);
        }
    }

    /**
     * Notify any registered observers that the parent located at {@code parentPosition}
     * has {@code itemCount} children that have been removed from the data set, previously
     * located at {@code childPositionStart} onwards. The child previously located at and
     * after {@code childPositionStart} may now be found at {@code childPositionStart - itemCount}.
     * <p>
     * This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their positions
     * may be altered.
     *
     * @param parentPosition Position of the parent which has a child removed from, relative
     *                       to the list of parents only.
     * @param childPositionStart Position of the first child that has been removed, relative
     *                           to children of the parent specified by {@code parentPosition} only.
     * @param itemCount number of children removed
     */
    @UiThread
    public void notifyChildRangeRemoved(int parentPosition, int childPositionStart, int itemCount) {
        int flatParentPosition = getFlatParentPosition(parentPosition);
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        parentWrapper.setParent(mParentList.get(parentPosition));

        if (parentWrapper.isExpanded()) {
            for (int i = 0; i < itemCount; i++) {
                mFlatItemList.remove(flatParentPosition + childPositionStart + 1);
            }
            notifyItemRangeRemoved(flatParentPosition + childPositionStart + 1, itemCount);
        }
    }

    /**
     * Notify any registered observers that the parent at {@code parentPosition} has
     * a child located at {@code childPosition} that has changed.
     * <p>
     * This is an item change event, not a structural change event. It indicates that any
     * reflection of the data at {@code childPosition} is out of date and should be updated.
     * The parent at {@code childPosition} retains the same identity.
     *
     * @param parentPosition Position of the parent which has a child that has changed
     * @param childPosition Position of the child that has changed
     */
    @UiThread
    public void notifyChildChanged(int parentPosition, int childPosition) {
        P parent = mParentList.get(parentPosition);
        int flatParentPosition = getFlatParentPosition(parentPosition);
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        parentWrapper.setParent(parent);
        if (parentWrapper.isExpanded()) {
            int flatChildPosition = flatParentPosition + childPosition + 1;
            ExpandableWrapper<P, C> child = parentWrapper.getWrappedChildList().get(childPosition);
            mFlatItemList.set(flatChildPosition, child);
            notifyItemChanged(flatChildPosition);
        }
    }

    /**
     * Notify any registered observers that the parent at {@code parentPosition} has
     * {@code itemCount} children starting at {@code childPositionStart} that have changed.
     * <p>
     * This is an item change event, not a structural change event. It indicates that any
     * The parent at {@code childPositionStart} retains the same identity.
     * reflection of the set of {@code itemCount} children starting at {@code childPositionStart}
     * are out of date and should be updated.
     *
     * @param parentPosition Position of the parent who has a child that has changed
     * @param childPositionStart Position of the first child that has changed
     * @param itemCount number of children changed
     */
    @UiThread
    public void notifyChildRangeChanged(int parentPosition, int childPositionStart, int itemCount) {
        P parent = mParentList.get(parentPosition);
        int flatParentPosition = getFlatParentPosition(parentPosition);
        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        parentWrapper.setParent(parent);
        if (parentWrapper.isExpanded()) {
            int flatChildPosition = flatParentPosition + childPositionStart + 1;
            for (int i = 0; i < itemCount; i++) {
                ExpandableWrapper<P, C> child
                        = parentWrapper.getWrappedChildList().get(childPositionStart + i);
                mFlatItemList.set(flatChildPosition + i, child);
            }
            notifyItemRangeChanged(flatChildPosition, itemCount);
        }
    }

    /**
     * Notify any registered observers that the child list item contained within the parent
     * at {@code parentPosition} has moved from {@code fromChildPosition} to {@code toChildPosition}.
     * <p>
     * <p>This is a structural change event. Representations of other existing items in the
     * data set are still considered up to date and will not be rebound, though their
     * positions may be altered.</p>
     *
     * @param parentPosition Position of the parent which has a child that has moved
     * @param fromChildPosition Previous position of the child
     * @param toChildPosition New position of the child
     */
    @UiThread
    public void notifyChildMoved(int parentPosition, int fromChildPosition, int toChildPosition) {
        P parent = mParentList.get(parentPosition);
        int flatParentPosition = getFlatParentPosition(parentPosition);

        ExpandableWrapper<P, C> parentWrapper = mFlatItemList.get(flatParentPosition);
        parentWrapper.setParent(parent);
        if (parentWrapper.isExpanded()) {
            ExpandableWrapper<P, C> fromChild = mFlatItemList.remove(flatParentPosition + 1 + fromChildPosition);
            mFlatItemList.add(flatParentPosition + 1 + toChildPosition, fromChild);
            notifyItemMoved(flatParentPosition + 1 + fromChildPosition, flatParentPosition + 1 + toChildPosition);
        }
    }

    // endregion

    /**
     * Generates a full list of all parents and their children, in order.
     *
     * @param parentList A list of the parents from
     *                   the {@link ExpandableRecyclerAdapter}
     * @return A list of all parents and their children, expanded
     */
    private List<ExpandableWrapper<P, C>> generateFlattenedParentChildList(List<P> parentList) {
        List<ExpandableWrapper<P, C>> flatItemList = new ArrayList<>();

        int parentCount = parentList.size();
        for (int i = 0; i < parentCount; i++) {
            P parent = parentList.get(i);
            generateParentWrapper(flatItemList, parent, parent.isInitiallyExpanded());
        }

        return flatItemList;
    }

    /**
     * Generates a full list of all parents and their children, in order. Uses Map to preserve
     * last expanded state.
     *
     * @param parentList A list of the parents from
     *                   the {@link ExpandableRecyclerAdapter}
     * @param savedLastExpansionState A map of the last expanded state for a given parent key.
     * @return A list of all parents and their children, expanded accordingly
     */
    private List<ExpandableWrapper<P, C>> generateFlattenedParentChildList(List<P> parentList, Map<P, Boolean> savedLastExpansionState) {
        List<ExpandableWrapper<P, C>> flatItemList = new ArrayList<>();

        int parentCount = parentList.size();
        for (int i = 0; i < parentCount; i++) {
            P parent = parentList.get(i);
            Boolean lastExpandedState = savedLastExpansionState.get(parent);
            boolean shouldExpand = lastExpandedState == null ? parent.isInitiallyExpanded() : lastExpandedState;

            generateParentWrapper(flatItemList, parent, shouldExpand);
        }

        return flatItemList;
    }

    private void generateParentWrapper(List<ExpandableWrapper<P, C>> flatItemList, P parent, boolean shouldExpand) {
        ExpandableWrapper<P, C> parentWrapper = new ExpandableWrapper<>(parent);
        flatItemList.add(parentWrapper);
        if (shouldExpand) {
            generateExpandedChildren(flatItemList, parentWrapper);
        }
    }

    private void generateExpandedChildren(List<ExpandableWrapper<P, C>> flatItemList, ExpandableWrapper<P, C> parentWrapper) {
        parentWrapper.setExpanded(true);

        List<ExpandableWrapper<P, C>> wrappedChildList = parentWrapper.getWrappedChildList();
        int childCount = wrappedChildList.size();
        for (int j = 0; j < childCount; j++) {
            ExpandableWrapper<P, C> childWrapper = wrappedChildList.get(j);
            flatItemList.add(childWrapper);
        }
    }

    /**
     * Generates a HashMap used to store expanded state for items in the list
     * on configuration change or whenever onResume is called.
     *
     * @return A HashMap containing the expanded state of all parents
     */
    @NonNull
    @UiThread
    private HashMap<Integer, Boolean> generateExpandedStateMap() {
        HashMap<Integer, Boolean> parentHashMap = new HashMap<>();
        int childCount = 0;

        int listItemCount = mFlatItemList.size();
        for (int i = 0; i < listItemCount; i++) {
            if (mFlatItemList.get(i) != null) {
                ExpandableWrapper<P, C> listItem = mFlatItemList.get(i);
                if (listItem.isParent()) {
                    parentHashMap.put(i - childCount, listItem.isExpanded());
                } else {
                    childCount++;
                }
            }
        }

        return parentHashMap;
    }

    /**
     * Gets the index of a ExpandableWrapper within the helper item list based on
     * the index of the ExpandableWrapper.
     *
     * @param parentPosition The index of the parent in the list of parents
     * @return The index of the parent in the merged list of children and parents
     */
    @UiThread
    private int getFlatParentPosition(int parentPosition) {
        int parentCount = 0;
        int listItemCount = mFlatItemList.size();
        for (int i = 0; i < listItemCount; i++) {
            if (mFlatItemList.get(i).isParent()) {
                parentCount++;

                if (parentCount > parentPosition) {
                    return i;
                }
            }
        }

        return INVALID_FLAT_POSITION;
    }
}
