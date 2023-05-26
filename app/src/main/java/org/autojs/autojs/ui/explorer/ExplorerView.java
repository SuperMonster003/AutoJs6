package org.autojs.autojs.ui.explorer;

import static org.autojs.autojs.model.explorer.ExplorerDirPage.createRoot;
import static org.autojs.autojs.model.explorer.Explorers.workspace;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.autojs.autojs.model.explorer.Explorer;
import org.autojs.autojs.model.explorer.ExplorerChangeEvent;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout;
import org.autojs.autojs.ui.viewmodel.ExplorerItemList;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs.groundwork.WrapContentGridLayoutManger;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on 2017/8/21.
 * Modified by SuperMonster003 as of Apr 1, 2023.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint({"CheckResult", "NonConstantResourceId", "NotifyDataSetChanged"})
public class ExplorerView extends ThemeColorSwipeRefreshLayout implements SwipeRefreshLayout.OnRefreshListener {

    private static final String LOG_TAG = "ExplorerView";

    private boolean mProjectRecognitionEnabled = true;

    public interface OnItemClickListener {
        void onItemClick(View view, ExplorerItem item);
    }

    public interface OnItemOperateListener {
        void onItemOperated(ExplorerItem item);
    }

    protected static final int VIEW_TYPE_ITEM = 0;
    protected static final int VIEW_TYPE_PAGE = 1;
    // category是类别，也即"文件", "文件夹"那两个
    protected static final int VIEW_TYPE_CATEGORY = 2;

    private static final int positionOfCategoryDir = 0;

    protected OnItemClickListener onItemClickListener;
    protected ExplorerItem selectedItem;

    private RecyclerView mExplorerItemListView;
    private ExplorerProjectToolbar mProjectToolbar;
    private final ExplorerAdapter mExplorerAdapter = new ExplorerAdapter();
    private Function<ExplorerItem, Boolean> mFilter;
    private OnItemOperateListener mOnItemOperateListener;
    private Explorer mExplorer;
    private final Stack<ExplorerPageState> mPageStateHistories = new Stack<>();

    private int mDirectorySpanSize = 2;

    boolean isDirSortMenuShowing = false;

    ExplorerPageState currentPageState = new ExplorerPageState();

    ExplorerItemList explorerItemList = new ExplorerItemList(getContext());

    public ExplorerView(Context context) {
        super(context);
        init();
    }

    public ExplorerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExplorerPage getCurrentPage() {
        return currentPageState.page;
    }

    public void setRootPage(ExplorerPage page) {
        setCurrentPageState(new ExplorerPageState(page));
        loadItemList();
    }

    private void setCurrentPageState(ExplorerPageState currentPageState) {
        this.currentPageState = currentPageState;
        if (isProjectRecognitionEnabled() && ProjectConfig.isProject(getCurrentPage())) {
            mProjectToolbar.setVisibility(VISIBLE);
            mProjectToolbar.setProject(currentPageState.page.toScriptFile());
        } else {
            mProjectToolbar.setVisibility(GONE);
        }
    }

    private boolean isProjectRecognitionEnabled() {
        return mProjectRecognitionEnabled;
    }

    public void setProjectRecognitionEnabled(boolean b) {
        mProjectRecognitionEnabled = b;
    }

    public void setProjectToolbarRunnableOnly(boolean b) {
        mProjectToolbar.setRunnableOnly(b);
    }

    public void enterDirectChildPage(ExplorerPage childItemGroup) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) mExplorerItemListView.getLayoutManager();
        if (layoutManager != null) {
            // @Overwrite by SuperMonster003 on Apr 3, 2023.
            //  ! Should be "first" instead of "last".
            // mCurrentPageState.scrollY = layoutManager.findLastCompletelyVisibleItemPosition();
            currentPageState.scrollY = layoutManager.findFirstCompletelyVisibleItemPosition();
        }
        mPageStateHistories.push(currentPageState);
        setCurrentPageState(new ExplorerPageState(childItemGroup));
        loadItemList();
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setExplorer(Explorer explorer, ExplorerPage rootPage) {
        if (mExplorer != null) {
            mExplorer.unregisterChangeListener(this);
        }
        mExplorer = explorer;
        setRootPage(rootPage);
        mExplorer.registerChangeListener(this);
    }

    public void setExplorer(Explorer explorer, ExplorerPage rootPage, ExplorerPage currentPage) {
        if (mExplorer != null) {
            mExplorer.unregisterChangeListener(this);
        }
        mExplorer = explorer;
        setCurrentPageState(new ExplorerPageState(rootPage));
        mExplorer.registerChangeListener(this);
        enterChildPage(currentPage);
    }

    public void setDefaultExplorer() {
        setExplorer(EnvironmentUtils.getExternalStoragePath(), WorkingDirectoryUtils.getPath());
    }

    public void setExplorer(@Nullable String rootPath, @NotNull String currentPath) {
        setExplorer(workspace(), createRoot(Objects.requireNonNullElseGet(rootPath, WorkingDirectoryUtils::getPath)), createRoot(currentPath));
    }

    public void enterChildPage(ExplorerPage childPage) {
        setCurrentPageState(new ExplorerPageState(childPage));
        loadItemList();
    }

    public void setOnItemOperateListener(OnItemOperateListener onItemOperateListener) {
        mOnItemOperateListener = onItemOperateListener;
    }

    public void setOnProjectToolbarOperateListener(ExplorerProjectToolbar.OnOperateListener onOperateListener) {
        mProjectToolbar.setOnOperateListener(onOperateListener);
    }

    public void setOnProjectToolbarClickListener(ExplorerProjectToolbar.OnClickListener onClickListener) {
        mProjectToolbar.setOnClickListener(onClickListener);
    }

    public boolean canGoBack() {
        return !mPageStateHistories.empty();
    }

    public void goBack() {
        setCurrentPageState(mPageStateHistories.pop());
        loadItemList();
    }

    public void saveViewStates() {
        saveExplorerState();
        savePageState();
    }

    public void restoreViewStates() {
        restoreExplorerState();
        restorePageState();
    }

    private void saveSortConfig() {
        explorerItemList.saveSortConfig();
    }

    private void restoreSortConfig() {
        explorerItemList.restoreSortConfig();
    }

    private void savePageState() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) mExplorerItemListView.getLayoutManager();
        if (layoutManager != null) {
            int currentScrollY = layoutManager.findFirstCompletelyVisibleItemPosition();
            Pref.putInt(getPrefKey("page_state"), currentScrollY);
        }
    }

    private void restorePageState() {
        int scrollY = Pref.getInt(getPrefKey("page_state"), -1);
        if (scrollY > 0) {
            currentPageState.scrollY = scrollY;
            Observable.empty()
                    .subscribeOn(Schedulers.io())
                    .subscribe(o -> {
                        notifyDataSetChanged();
                        post(this::scrollToPositionOrdinarily);
                    });
        }
    }

    // TODO by SuperMonster003 on Apr 1, 2023.
    //  ! Apparently, a more graceful way is needed.
    private void saveExplorerState() {
        String currentPath = getCurrentPage().getPath();
        Pref.putString(getPrefKey("explorer_current"), currentPath);

        String rootPath = mPageStateHistories.isEmpty() ? WorkingDirectoryUtils.getPath() : mPageStateHistories.firstElement().page.getPath();
        Pref.putString(getPrefKey("explorer_root"), rootPath);

        LinkedList<String> histories = new LinkedList<>();
        for (ExplorerPageState explorerPageState : mPageStateHistories) {
            histories.add(explorerPageState.page.getPath()
                    + "," + explorerPageState.scrollY
                    + "," + explorerPageState.dirsCollapsed
                    + "," + explorerPageState.filesCollapsed);
        }
        Pref.putLinkedList(getPrefKey("explorer_histories"), histories);
    }

    // TODO by SuperMonster003 on Apr 1, 2023.
    //  ! Apparently, a more graceful way is needed.
    private void restoreExplorerState() {
        String storedCurrentPath = Pref.getString(getPrefKey("explorer_current"), null);
        if (storedCurrentPath != null) {
            String storedRootPath = Pref.getString(getPrefKey("explorer_root"), null);
            setExplorer(storedRootPath, storedCurrentPath);
        } else {
            setDefaultExplorer();
        }

        LinkedList<String> storedHistories = Pref.getLinkedList(getPrefKey("explorer_histories"));
        for (int i = 0; i < storedHistories.size(); i++) {
            String dataString = storedHistories.get(i);

            String[] split = dataString.split(",");
            String pagePath = split[0];
            String scrollY = split[1];
            String dirsCollapsed = split[2];
            String filesCollapsed = split[3];

            ExplorerPageState pageState = new ExplorerPageState();
            pageState.page = createRoot(pagePath);
            pageState.scrollY = Integer.parseInt(scrollY);
            pageState.dirsCollapsed = Boolean.parseBoolean(dirsCollapsed);
            pageState.filesCollapsed = Boolean.parseBoolean(filesCollapsed);

            mPageStateHistories.push(pageState);
        }
    }

    public static void clearViewStates() {
        Pref.remove(getPrefKey("page_state"));
        Pref.remove(getPrefKey("explorer_current"));
        Pref.remove(getPrefKey("explorer_root"));
        Pref.remove(getPrefKey("explorer_histories"));
    }

    @NonNull
    public static String getPrefKey(String key) {
        return ExplorerView.class.getSimpleName() + '.' + key;
    }

    public boolean canGoUp() {
        return !EnvironmentUtils.getExternalStoragePath().startsWith(getCurrentPage().getPath());
    }

    public void goUp() {
        mPageStateHistories.push(currentPageState);
        setCurrentPageState(new ExplorerPageState(createRoot(new File(getCurrentPage().getPath()).getParent())));
        loadItemList();
    }

    public void setDirectorySpanSize(int directorySpanSize) {
        mDirectorySpanSize = directorySpanSize;
    }

    public void setFilter(Function<ExplorerItem, Boolean> filter) {
        mFilter = filter;
        reload();
    }

    public void reload() {
        loadItemList();
    }

    private void init() {
        Log.d(LOG_TAG, "item bg = " + Integer.toHexString(ContextCompat.getColor(getContext(), R.color.item_background)));
        restoreSortConfig();
        setOnRefreshListener(this);
        inflate(getContext(), R.layout.explorer_view, this);
        mExplorerItemListView = findViewById(R.id.explorer_item_list);
        mProjectToolbar = findViewById(R.id.project_toolbar);
        initExplorerItemListView();
    }

    private void initExplorerItemListView() {
        mExplorerItemListView.setAdapter(mExplorerAdapter);
        WrapContentGridLayoutManger manager = new WrapContentGridLayoutManger(getContext(), 2);
        manager.setDebugInfo("ExplorerView");
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                //For directories
                if (position > positionOfCategoryDir && position < positionOfCategoryFile()) {
                    return mDirectorySpanSize;
                }
                //For files and category
                return 2;
            }
        });
        mExplorerItemListView.setLayoutManager(manager);
    }

    private int positionOfCategoryFile() {
        if (currentPageState.dirsCollapsed)
            return 1;
        return explorerItemList.groupCount() + 1;
    }

    private void loadItemList() {
        setRefreshing(true);
        mExplorer.fetchChildren(getCurrentPage())
                .subscribeOn(Schedulers.io())
                .flatMapObservable(page -> {
                    currentPageState.page = page;
                    return Observable.fromIterable(page);
                })
                .filter(f -> mFilter == null || mFilter.apply(f))
                .collectInto(explorerItemList.cloneConfig(), ExplorerItemList::add)
                .observeOn(Schedulers.computation())
                .doOnSuccess(ExplorerItemList::sort)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    explorerItemList = list;
                    notifyDataSetChanged();
                    setRefreshing(false);
                    post(this::scrollToPositionSmoothly);
                });
    }

    private void scrollToPositionOrdinarily() {
        RecyclerView.LayoutManager layoutManager = mExplorerItemListView.getLayoutManager();
        int position = currentPageState.scrollY;

        if (layoutManager != null) {
            layoutManager.scrollToPosition(position);
        }
    }

    private void scrollToPositionSmoothly() {
        RecyclerView.LayoutManager layoutManager = mExplorerItemListView.getLayoutManager();
        int position = currentPageState.scrollY;

        RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };
        smoothScroller.setTargetPosition(position);
        if (layoutManager != null) {
            layoutManager.startSmoothScroll(smoothScroller);
        }
    }

    public void notifyDataSetChanged() {
        if (mExplorerAdapter != null) {
            mExplorerAdapter.notifyDataSetChanged();
        }
    }

    @Subscribe
    public void onExplorerChange(ExplorerChangeEvent event) {
        Log.d(LOG_TAG, "on explorer change: " + event);
        if ((event.getAction() == ExplorerChangeEvent.ALL)) {
            loadItemList();
            return;
        }
        String currentDirPath = getCurrentPage().getPath();
        String changedDirPath = event.getPage().getPath();
        ExplorerItem item = event.getItem();
        String changedItemPath = item == null ? null : item.getPath();
        if (currentDirPath.equals(changedItemPath) || (currentDirPath.equals(changedDirPath) &&
                event.getAction() == ExplorerChangeEvent.CHILDREN_CHANGE)) {
            loadItemList();
            return;
        }
        if (currentDirPath.equals(changedDirPath)) {
            int i;
            switch (event.getAction()) {
                case ExplorerChangeEvent.CHANGE -> {
                    i = explorerItemList.update(item, event.getNewItem());
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemChanged(item, i);
                    }
                }
                case ExplorerChangeEvent.CREATE -> {
                    explorerItemList.insertAtFront(event.getNewItem());
                    mExplorerAdapter.notifyItemInserted(event.getNewItem(), 0);
                }
                case ExplorerChangeEvent.REMOVE -> {
                    i = explorerItemList.remove(item);
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemRemoved(item, i);
                    }
                }
            }
        }
    }

    @Override
    public void onRefresh() {
        currentPageState.scrollY = 0;
        mExplorer.notifyChildrenChanged(getCurrentPage());
        mProjectToolbar.refresh();
    }

    public ScriptFile getCurrentDirectory() {
        return getCurrentPage().toScriptFile();
    }

    void resetSucceeded() {
        ViewUtils.showSnack(this, R.string.text_reset_succeed);
    }

    void resetFailed() {
        ViewUtils.showSnack(this, R.string.text_reset_to_initial_content_only_for_assets, true);
    }

    protected void notifyItemOperated() {
        if (mOnItemOperateListener != null) {
            mOnItemOperateListener.onItemOperated(selectedItem);
        }
    }

    void sort(final int sortType, final boolean isDir, boolean isFileSortedAscending) {
        setRefreshing(true);

        Callable<ExplorerItemList> explorerItemListCallable = () -> {
            if (isDir) {
                explorerItemList.sortDirs(sortType, isFileSortedAscending);
            } else {
                explorerItemList.sortFiles(sortType, isFileSortedAscending);
            }
            return explorerItemList;
        };
        Observable.fromCallable(explorerItemListCallable)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(o -> {
                    notifyDataSetChanged();
                    setRefreshing(false);
                    saveSortConfig();
                });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mExplorer != null)
            mExplorer.registerChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mExplorer.unregisterChangeListener(this);
        mPageStateHistories.clear();
    }

    protected BindableViewHolder<?> onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            return new ExplorerItemViewHolder(this, inflater.inflate(R.layout.explorer_file, parent, false));
        } else if (viewType == VIEW_TYPE_PAGE) {
            return new ExplorerPageViewHolder(this, inflater.inflate(R.layout.explorer_directory, parent, false));
        } else {
            return new ExplorerCategoryViewHolder(this, inflater.inflate(R.layout.explorer_category, parent, false));
        }
    }

    protected RecyclerView getExplorerItemListView() {
        return mExplorerItemListView;
    }

    private class ExplorerAdapter extends RecyclerView.Adapter<BindableViewHolder<?>> {

        @NonNull
        @Override
        public BindableViewHolder<?> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            return ExplorerView.this.onCreateViewHolder(inflater, parent, viewType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull BindableViewHolder<?> holder, int position) {
            int positionOfCategoryFile = positionOfCategoryFile();
            if (position == positionOfCategoryDir || position == positionOfCategoryFile) {
                ((BindableViewHolder<Boolean>) holder).bind(position == positionOfCategoryDir, position);
                return;
            }
            if (position < positionOfCategoryFile) {
                ((BindableViewHolder<ExplorerPage>) holder).bind(explorerItemList.getDirItem(position - 1), position);
                return;
            }
            ((BindableViewHolder<ExplorerItem>) holder).bind(explorerItemList.getFileItem(position - positionOfCategoryFile - 1), position);
        }

        @Override
        public int getItemViewType(int position) {
            int positionOfCategoryFile = positionOfCategoryFile();
            if (position == positionOfCategoryDir || position == positionOfCategoryFile) {
                return VIEW_TYPE_CATEGORY;
            } else if (position < positionOfCategoryFile) {
                return VIEW_TYPE_PAGE;
            } else {
                return VIEW_TYPE_ITEM;
            }
        }

        int getItemPosition(ExplorerItem item, int i) {
            if (item instanceof ExplorerPage) {
                return i + positionOfCategoryDir + 1;
            }
            return i + positionOfCategoryFile() + 1;
        }

        public void notifyItemChanged(ExplorerItem item, int i) {
            notifyItemChanged(getItemPosition(item, i));
        }

        public void notifyItemRemoved(ExplorerItem item, int i) {
            notifyItemRemoved(getItemPosition(item, i));
        }

        public void notifyItemInserted(ExplorerItem item, int i) {
            notifyItemInserted(getItemPosition(item, i));
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (!currentPageState.dirsCollapsed) {
                count += explorerItemList.groupCount();
            }
            if (!currentPageState.filesCollapsed) {
                count += explorerItemList.itemCount();
            }
            return count + 2;
        }
    }

    static class ExplorerPageState {

        ExplorerPage page;

        boolean dirsCollapsed;

        boolean filesCollapsed;

        int scrollY;

        ExplorerPageState() {
            // Empty constructor.
        }

        ExplorerPageState(ExplorerPage page) {
            this.page = page;
        }

    }

}
