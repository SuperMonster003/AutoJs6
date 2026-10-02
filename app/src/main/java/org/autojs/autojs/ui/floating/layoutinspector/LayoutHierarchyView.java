package org.autojs.autojs.ui.floating.layoutinspector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.core.accessibility.NodeInfo;
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorMatchView;
import org.autojs.autojs.ui.widget.LevelBeamView;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import kotlin.ranges.IntRange;
import pl.openrnd.multilevellistview.ItemInfo;
import pl.openrnd.multilevellistview.MultiLevelListAdapter;
import pl.openrnd.multilevellistview.MultiLevelListView;
import pl.openrnd.multilevellistview.NestType;
import pl.openrnd.multilevellistview.OnItemClickListener;

/**
 * Expandable tree of the captured node hierarchy. Every row shows the simplified class name
 * followed by a summary of the node's id, text, desc and boolean flags; the selected row and all
 * of its ancestors are highlighted, and the on-screen bounds of the selected node (red) and its
 * ancestors (accent) are outlined over the app underneath.
 * <p>
 * All row state (text, colors, backgrounds) is derived from the data inside
 * {@link Adapter#getViewForObject} so recycled rows never keep stale highlights.
 * <p>
 * zh-CN: 已捕获节点层次的可展开树. 每行显示简化类名及节点 id / text / desc / 布尔标志的摘要; 选中行及其全部祖先行会被高亮,
 * 并在下方应用之上勾出选中节点 (红色) 与其祖先 (强调色) 的屏幕边框.
 * 行状态 (文本, 颜色, 背景) 全部在 {@link Adapter#getViewForObject} 中由数据推导, 复用的行不会残留旧的高亮.
 *
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
public class LayoutHierarchyView extends MultiLevelListView {

    public interface OnItemLongClickListener {
        void onItemLongClick(View view, NodeInfo nodeInfo);
    }

    private static final String WIDGET_PACKAGE_PREFIX = "android.widget.";
    private static final float ROW_TEXT_SIZE_SP = 14f;
    private static final float ROW_EXTRA_WIDTH_DP = 5 + 3 + 3 + 10 + 10 + 5 + 8;
    private static final int MAX_CONTENT_WIDTH_SCREENS = 3;

    private Adapter mAdapter;
    private OnItemLongClickListener mOnItemLongClickListener;
    private final AdapterView.OnItemLongClickListener mOnItemLongClickListenerProxy = (parent, view, position, id) -> {
        if (mOnItemLongClickListener != null) {
            mOnItemLongClickListener.onItemLongClick(view, ((ViewHolder) view.getTag()).nodeInfo);
            return true;
        }
        return false;
    };

    private Paint mPaint;
    private Paint mAncestorBoundsPaint;
    private int[] mBoundsInScreen;
    private int mStatusBarHeight;
    private NodeInfo mClickedNodeInfo;

    private boolean mShowClickedNodeBounds;
    private int mClickedColor = 0x99b2b3b7;
    private int mDefaultTextColor;
    private int mHighlightTextColor;
    private NodeInfo mRootNode;
    private int mContentWidth;
    private TextPaint mRowTextPaint;
    private float mRowBeamPadding;
    private float mRowBeamStep;
    private float mRowExtraWidth;
    private final Set<NodeInfo> mInitiallyExpandedNodes = new HashSet<>();
    /** Ancestors of the selected node, root included, the node itself excluded. zh-CN: 选中节点的祖先集合, 含根, 不含自身. */
    private final Set<NodeInfo> mNodePath = new HashSet<>();

    /** The active search, which prunes the tree to its kept nodes and highlights its matches (roadmap D3). zh-CN: 当前搜索, 把树裁剪为其保留节点并高亮其匹配项 (Roadmap D3). */
    @Nullable
    private InspectorMatchView mMatch;

    /** Whether the tree is currently pruned by mMatch. zh-CN: 树当前是否被 mMatch 裁剪. */
    private boolean mFiltered;

    /** The rows expanded when the search started, restored when it is cleared. zh-CN: 搜索开始时已展开的行, 清空搜索时还原. */
    @Nullable
    private Set<NodeInfo> mExpandedBeforeMatch;

    private int mMatchHighlightColor = 0x4D2196F3;

    /** Opacity of the rows kept only because a descendant matches. zh-CN: 仅因后代匹配而保留的行的不透明度. */
    private static final float ANCESTOR_ROW_ALPHA = 0.55f;

    public LayoutHierarchyView(Context context) {
        super(context);
        init();
    }

    public LayoutHierarchyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LayoutHierarchyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setShowClickedNodeBounds(boolean showClickedNodeBounds) {
        mShowClickedNodeBounds = showClickedNodeBounds;
    }

    public void setClickedBackgroundColor(int clickedColor) {
        mClickedColor = clickedColor;
    }

    /** Text color of the selected row and its ancestors. zh-CN: 选中行及其祖先行的文字颜色. */
    public void setHighlightTextColor(int color) {
        mHighlightTextColor = color;
        mAncestorBoundsPaint.setColor(ColorUtils.applyAlpha(color, 0.6));
        mAdapter.notifyDataSetChanged();
    }

    /** Background of the matched part of a row's text while a search is active. zh-CN: 搜索生效时行文本中命中部分的背景色. */
    public void setMatchHighlightColor(int color) {
        mMatchHighlightColor = color;
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Prunes the tree to the nodes [match] keeps (matches and their ancestors, all expanded) and
     * highlights the matches; null or an unfiltered match restores the full tree with the rows that
     * were expanded before the search started. Call again whenever the match changes.
     * zh-CN: 把树裁剪为 [match] 保留的节点 (匹配项及其祖先, 全部展开) 并高亮匹配项; null 或无筛选的匹配结果恢复完整树, 并还原
     * 搜索开始前已展开的行. 匹配结果变化时需再次调用.
     */
    public void setMatch(@Nullable InspectorMatchView match) {
        // The match view is usually the live search session, so the previous state cannot be read
        // back from it: it is remembered in mFiltered.
        // zh-CN: 匹配视图通常就是实时的搜索会话, 无法从它读回之前的状态: 由 mFiltered 记住.
        boolean wasFiltered = mFiltered;
        boolean filtered = match != null && !match.isUnfiltered();
        mMatch = match;
        mFiltered = filtered;
        if (filtered && !wasFiltered) {
            mExpandedBeforeMatch = new HashSet<>();
            for (Object expanded : mAdapter.getExpandedObjects()) {
                mExpandedBeforeMatch.add((NodeInfo) expanded);
            }
        } else if (!filtered && wasFiltered) {
            mInitiallyExpandedNodes.clear();
            if (mExpandedBeforeMatch != null) {
                mInitiallyExpandedNodes.addAll(mExpandedBeforeMatch);
            }
            // The selection made during the search stays reachable. zh-CN: 搜索期间做出的选择保持可见.
            if (mClickedNodeInfo != null) {
                mInitiallyExpandedNodes.addAll(mNodePath);
            }
            mExpandedBeforeMatch = null;
        }
        mAdapter.reloadData();
        invalidate();
    }

    private boolean isFiltered() {
        return mFiltered;
    }

    private CharSequence highlight(String summary) {
        IntRange range = mMatch == null ? null : mMatch.highlightRange(summary);
        if (range == null || range.isEmpty() || range.getLast() >= summary.length()) {
            return summary;
        }
        SpannableString spannable = new SpannableString(summary);
        spannable.setSpan(new BackgroundColorSpan(mMatchHighlightColor), range.getFirst(), range.getLast() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private void init() {
        mDefaultTextColor = getContext().getColor(R.color.day_night);
        mHighlightTextColor = getContext().getColor(R.color.layout_hierarchy_window_node_bounds);
        initRowMetrics();
        mAdapter = new Adapter();
        setAdapter(mAdapter);
        setNestType(NestType.MULTIPLE);
        ((ListView) getChildAt(0)).setOnItemLongClickListener(mOnItemLongClickListenerProxy);
        setWillNotDraw(false);
        initPaint();
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClicked(MultiLevelListView parent, View view, Object item, ItemInfo itemInfo) {
                setClickedItem((NodeInfo) item);
            }

            @Override
            public void onGroupItemClicked(MultiLevelListView parent, View view, Object item, ItemInfo itemInfo) {
                setClickedItem((NodeInfo) item);
            }
        });
    }

    private void setClickedItem(NodeInfo item) {
        mClickedNodeInfo = item;
        rebuildNodePath(item);
        mAdapter.notifyDataSetChanged();
        invalidate();
    }

    private void rebuildNodePath(@Nullable NodeInfo node) {
        mNodePath.clear();
        if (node == null) return;
        NodeInfo current = node.getParent();
        while (current != null) {
            mNodePath.add(current);
            current = current.getParent();
        }
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setColor(Color.DKGRAY);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(3);
        mAncestorBoundsPaint = new Paint();
        mAncestorBoundsPaint.setColor(ColorUtils.applyAlpha(mHighlightTextColor, 0.6));
        mAncestorBoundsPaint.setStyle(Paint.Style.STROKE);
        mAncestorBoundsPaint.setAntiAlias(true);
        mAncestorBoundsPaint.setStrokeWidth(2);
        mStatusBarHeight = ViewUtils.getStatusBarHeight(getContext());
    }

    public Paint getBoundsPaint() {
        return mPaint;
    }

    public void setRootNode(NodeInfo rootNodeInfo) {
        mRootNode = rootNodeInfo;
        mClickedNodeInfo = null;
        mNodePath.clear();
        mInitiallyExpandedNodes.clear();
        mAdapter.setDataItems(Collections.singletonList(rootNodeInfo));
    }

    public void setOnItemLongClickListener(final OnItemLongClickListener onNodeInfoSelectListener) {
        mOnItemLongClickListener = onNodeInfoSelectListener;
    }

    @Nullable
    public NodeInfo getClickedNode() {
        return mClickedNodeInfo;
    }

    /**
     * The list is as wide as its longest row so a wrapping {@link HorizontalScrollView} can reveal
     * deeply nested rows in full; without such a parent the width is the one given by the parent.
     * zh-CN: 列表宽度取最长行的宽度, 外层 {@link HorizontalScrollView} 可完整展示深层行; 没有这种父级时使用父级给定的宽度.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(Math.max(screenWidth, mContentWidth), screenWidth * MAX_CONTENT_WIDTH_SCREENS);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void initRowMetrics() {
        mRowTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mRowTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE_SP, getResources().getDisplayMetrics()));
        mRowBeamPadding = getResources().getDimension(R.dimen.level_beam_view_padding_left) + getResources().getDimension(R.dimen.level_beam_view_padding_right);
        mRowBeamStep = getResources().getDimension(R.dimen.level_beam_view_line_width) + getResources().getDimension(R.dimen.level_beam_view_line_offset);
        mRowExtraWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ROW_EXTRA_WIDTH_DP, getResources().getDisplayMetrics());
    }

    /**
     * Recomputes the width needed by the widest row currently displayed; collapsed sub-trees do
     * not count, so the list never scrolls into empty space.
     * zh-CN: 重新计算当前显示的最宽行所需宽度; 已收起的子树不计入, 列表不会滚动到空白区域.
     */
    private void updateContentWidth() {
        if (mAdapter == null || mRowTextPaint == null) return;
        float max = 0;
        for (int i = 0; i < mAdapter.getDisplayedItemCount(); i++) {
            NodeInfo node = (NodeInfo) mAdapter.getDisplayedObject(i);
            int level = mAdapter.getDisplayedItemInfo(i).getLevel();
            float width = mRowBeamPadding + (level + 1) * mRowBeamStep + mRowTextPaint.measureText(summarize(node)) + mRowExtraWidth;
            if (width > max) max = width;
        }
        int contentWidth = (int) Math.ceil(max);
        if (contentWidth != mContentWidth) {
            mContentWidth = contentWidth;
            requestLayout();
        }
    }

    private int horizontalScrollOffset() {
        ViewParent parent = getParent();
        return parent instanceof HorizontalScrollView ? ((HorizontalScrollView) parent).getScrollX() : 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBoundsInScreen == null) {
            mBoundsInScreen = new int[4];
            getLocationOnScreen(mBoundsInScreen);
            mStatusBarHeight = mBoundsInScreen[1];
        }
        if (mShowClickedNodeBounds && mClickedNodeInfo != null) {
            int dx = horizontalScrollOffset();
            for (NodeInfo ancestor : mNodePath) {
                LayoutBoundsView.drawRect(canvas, ancestor.getBoundsInScreen(), mStatusBarHeight, mAncestorBoundsPaint, dx);
            }
            LayoutBoundsView.drawRect(canvas, mClickedNodeInfo.getBoundsInScreen(), mStatusBarHeight, mPaint, dx);
        }
    }

    /**
     * Selects a node, opening the path down to it while keeping whatever the user has already
     * expanded, then scrolls the row into view.
     * zh-CN: 选中节点, 展开通往该节点的路径并保留用户已展开的部分, 然后把该行滚动到可见范围.
     */
    public void setSelectedNode(NodeInfo selectedNode) {
        List<NodeInfo> parents = new ArrayList<>();
        if (!searchNodeParents(selectedNode, mRootNode, parents)) {
            return;
        }
        mClickedNodeInfo = selectedNode;
        rebuildNodePath(selectedNode);
        mInitiallyExpandedNodes.clear();
        for (Object expanded : mAdapter.getExpandedObjects()) {
            mInitiallyExpandedNodes.add((NodeInfo) expanded);
        }
        mInitiallyExpandedNodes.addAll(parents);
        mAdapter.reloadData();
        scrollToNode(selectedNode);
    }

    private void scrollToNode(NodeInfo node) {
        int index = mAdapter.indexOfObject(node);
        if (index < 0) return;
        ListView listView = (ListView) getChildAt(0);
        listView.post(() -> {
            int visible = listView.getLastVisiblePosition() - listView.getFirstVisiblePosition();
            listView.setSelection(Math.max(0, index - Math.max(1, visible / 2)));
        });
    }

    private boolean searchNodeParents(NodeInfo nodeInfo, NodeInfo rootNode, List<NodeInfo> path) {
        if (rootNode == null) {
            return false;
        }
        path.add(rootNode);
        if (nodeInfo == rootNode) {
            return true;
        }
        for (NodeInfo child : rootNode.getChildren()) {
            if (searchNodeParents(nodeInfo, child, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * One-line summary of a node: the simplified class name followed by
     * {@code [id=...,text=...,desc=...,clickable,longClickable,scrollable,checkable,checked,selected,editable]}
     * where only the present attributes are listed.
     * zh-CN: 节点的单行摘要: 简化类名, 后接仅列出已具备属性的方括号摘要.
     */
    @NonNull
    public static String summarize(@NonNull NodeInfo nodeInfo) {
        StringBuilder builder = new StringBuilder(simplifyClassName(nodeInfo.getClassName()));
        List<String> details = new ArrayList<>();
        String id = nodeInfo.getId();
        if (id != null && !id.isEmpty()) {
            details.add("id=" + id);
        }
        String text = nodeInfo.getText();
        if (text != null && !text.isEmpty()) {
            details.add("text=" + escapeLineBreaks(text));
        }
        String desc = nodeInfo.getDesc();
        if (desc != null && !desc.isEmpty()) {
            details.add("desc=" + escapeLineBreaks(desc));
        }
        if (nodeInfo.getClickable()) details.add("clickable");
        if (nodeInfo.getLongClickable()) details.add("longClickable");
        if (nodeInfo.getScrollable()) details.add("scrollable");
        if (nodeInfo.getCheckable()) details.add("checkable");
        if (nodeInfo.getChecked()) details.add("checked");
        if (nodeInfo.getSelected()) details.add("selected");
        if (nodeInfo.getEditable()) details.add("editable");
        if (!details.isEmpty()) {
            builder.append(" [").append(String.join(",", details)).append("]");
        }
        return builder.toString();
    }

    private static String escapeLineBreaks(String s) {
        return s.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    @NonNull
    private static String simplifyClassName(@Nullable CharSequence className) {
        if (className == null) {
            return "";
        }
        String s = className.toString();
        if (s.startsWith(WIDGET_PACKAGE_PREFIX)) {
            s = s.substring(WIDGET_PACKAGE_PREFIX.length());
        }
        return s;
    }

    private static class ViewHolder {
        TextView nameView;
        TextView infoView;
        ImageView arrowView;
        LevelBeamView levelBeamView;
        NodeInfo nodeInfo;

        ViewHolder(View view) {
            infoView = view.findViewById(R.id.dataItemInfo);
            nameView = view.findViewById(R.id.dataItemName);
            arrowView = view.findViewById(R.id.dataItemArrow);
            levelBeamView = view.findViewById(R.id.dataItemLevelBeam);
        }
    }

    private class Adapter extends MultiLevelListAdapter {

        /** Every change of the displayed rows (expand, collapse, reload) may change the widest row. zh-CN: 显示行的每次变化 (展开, 收起, 重载) 都可能改变最宽行. */
        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateContentWidth();
        }

        @Override
        protected List<?> getSubObjects(Object object) {
            List<NodeInfo> children = ((NodeInfo) object).getChildren();
            if (!isFiltered()) {
                return children;
            }
            List<NodeInfo> kept = new ArrayList<>(children.size());
            for (NodeInfo child : children) {
                if (mMatch.isKept(child)) {
                    kept.add(child);
                }
            }
            return kept;
        }

        @Override
        protected boolean isExpandable(Object object) {
            return !getSubObjects(object).isEmpty();
        }

        @Override
        protected boolean isInitiallyExpanded(Object object) {
            return isFiltered() || mInitiallyExpandedNodes.contains((NodeInfo) object);
        }

        @SuppressLint("InflateParams")
        @Override
        public View getViewForObject(Object object, View convertView, ItemInfo itemInfo) {
            NodeInfo nodeInfo = (NodeInfo) object;
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.layout_hierarchy_view_item, null);
                viewHolder = new ViewHolder(convertView);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            viewHolder.nodeInfo = nodeInfo;
            String summary = summarize(nodeInfo);
            boolean filtered = isFiltered();
            boolean isMatch = filtered && mMatch.isMatched(nodeInfo);
            viewHolder.nameView.setText(isMatch ? highlight(summary) : summary);
            viewHolder.nameView.setAlpha(filtered && !isMatch ? ANCESTOR_ROW_ALPHA : 1f);

            boolean isSelected = nodeInfo == mClickedNodeInfo;
            boolean isAncestor = mNodePath.contains(nodeInfo);
            viewHolder.nameView.setTextColor(isSelected || isAncestor ? mHighlightTextColor : mDefaultTextColor);
            convertView.setBackgroundColor(isSelected ? mClickedColor : Color.TRANSPARENT);

            if (viewHolder.infoView.getVisibility() == VISIBLE) {
                viewHolder.infoView.setText(getItemInfoDsc(itemInfo));
            }

            if (itemInfo.isExpandable() && !isAlwaysExpanded()) {
                viewHolder.arrowView.setVisibility(View.VISIBLE);
                viewHolder.arrowView.setImageResource(itemInfo.isExpanded()
                        ? R.drawable.arrow_up
                        : R.drawable.arrow_down);
            } else {
                viewHolder.arrowView.setVisibility(View.GONE);
            }

            viewHolder.levelBeamView.setLevel(itemInfo.getLevel());
            return convertView;
        }

        private String getItemInfoDsc(ItemInfo itemInfo) {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format(java.util.Locale.getDefault(), "level[%d], idx in level[%d/%d]",
                    itemInfo.getLevel() + 1, /*Indexing starts from 0*/
                    itemInfo.getIdxInLevel() + 1 /*Indexing starts from 0*/,
                    itemInfo.getLevelSize()));

            if (itemInfo.isExpandable()) {
                builder.append(String.format(", expanded[%b]", itemInfo.isExpanded()));
            }
            return builder.toString();
        }
    }

}
