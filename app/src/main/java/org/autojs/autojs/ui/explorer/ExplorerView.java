package org.autojs.autojs.ui.explorer;

import static org.autojs.autojs.model.explorer.WorkspaceFileProvider.SAMPLE_PATH;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.autojs.autojs.model.explorer.Explorer;
import org.autojs.autojs.model.explorer.ExplorerChangeEvent;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.ExplorerSamplePage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout;
import org.autojs.autojs.ui.common.ScriptLoopDialog;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.ui.viewmodel.ExplorerItemList;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.ui.widget.FirstCharView;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.Observers;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.workground.WrapContentGridLayoutManger;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.Stack;
import java.util.concurrent.Callable;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on 2017/8/21.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint({"CheckResult", "NonConstantResourceId", "NotifyDataSetChanged"})
public class ExplorerView extends ThemeColorSwipeRefreshLayout implements SwipeRefreshLayout.OnRefreshListener, PopupMenu.OnMenuItemClickListener {

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

    private ExplorerItemList mExplorerItemList = new ExplorerItemList(getContext());
    private RecyclerView mExplorerItemListView;
    private ExplorerProjectToolbar mProjectToolbar;
    private final ExplorerAdapter mExplorerAdapter = new ExplorerAdapter();
    protected OnItemClickListener mOnItemClickListener;
    private Function<ExplorerItem, Boolean> mFilter;
    private OnItemOperateListener mOnItemOperateListener;
    protected ExplorerItem mSelectedItem;
    private Explorer mExplorer;
    private final Stack<ExplorerPageState> mPageStateHistory = new Stack<>();
    private ExplorerPageState mCurrentPageState = new ExplorerPageState();
    private boolean mDirSortMenuShowing = false;
    private int mDirectorySpanSize = 2;

    public ExplorerView(Context context) {
        super(context);
        init();
    }

    public ExplorerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExplorerPage getCurrentPage() {
        return mCurrentPageState.page;
    }

    public void setRootPage(ExplorerPage page) {
        mPageStateHistory.clear();
        setCurrentPageState(new ExplorerPageState(page));
        loadItemList();
    }

    private void setCurrentPageState(ExplorerPageState currentPageState) {
        mCurrentPageState = currentPageState;
        if (isProjectRecognitionEnabled() && ProjectConfig.isProject(mCurrentPageState.page)) {
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

    protected void enterDirectChildPage(ExplorerPage childItemGroup) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) mExplorerItemListView.getLayoutManager();
        if (layoutManager != null) {
            mCurrentPageState.scrollY = layoutManager.findLastCompletelyVisibleItemPosition();
        }
        mPageStateHistory.push(mCurrentPageState);
        setCurrentPageState(new ExplorerPageState(childItemGroup));
        loadItemList();
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    public void setSortConfig(ExplorerItemList.SortConfig sortConfig) {
        mExplorerItemList.setSortConfig(sortConfig);
    }

    public ExplorerItemList.SortConfig getSortConfig() {
        return mExplorerItemList.getSortConfig();
    }

    public void setExplorer(Explorer explorer, ExplorerPage rootPage) {
        if (mExplorer != null)
            mExplorer.unregisterChangeListener(this);
        mExplorer = explorer;
        setRootPage(rootPage);
        mExplorer.registerChangeListener(this);
    }

    public void setExplorer(Explorer explorer, ExplorerPage rootPage, ExplorerPage currentPage) {
        if (mExplorer != null)
            mExplorer.unregisterChangeListener(this);
        mExplorer = explorer;
        mPageStateHistory.clear();
        setCurrentPageState(new ExplorerPageState(rootPage));
        mExplorer.registerChangeListener(this);
        enterChildPage(currentPage);
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
        return !mPageStateHistory.empty();
    }

    public void goBack() {
        setCurrentPageState(mPageStateHistory.pop());
        loadItemList();
    }

    public boolean canGoUp() {
        return !EnvironmentUtils.getExternalStoragePath().startsWith(mCurrentPageState.page.getPath());
    }

    public void goUp() {
        mPageStateHistory.push(mCurrentPageState);
        setCurrentPageState(new ExplorerPageState(ExplorerDirPage.createRoot(new File(mCurrentPageState.page.getPath()).getParent())));
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
        if (mCurrentPageState.dirsCollapsed)
            return 1;
        return mExplorerItemList.groupCount() + 1;
    }

    private void loadItemList() {
        setRefreshing(true);
        mExplorer.fetchChildren(mCurrentPageState.page)
                .subscribeOn(Schedulers.io())
                .flatMapObservable(page -> {
                    mCurrentPageState.page = page;
                    return Observable.fromIterable(page);
                })
                .filter(f -> mFilter == null || mFilter.apply(f))
                .collectInto(mExplorerItemList.cloneConfig(), ExplorerItemList::add)
                .observeOn(Schedulers.computation())
                .doOnSuccess(ExplorerItemList::sort)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    mExplorerItemList = list;
                    notifyDataSetChanged();
                    setRefreshing(false);
                    post(() -> mExplorerItemListView.scrollToPosition(mCurrentPageState.scrollY));
                });
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
        String currentDirPath = mCurrentPageState.page.getPath();
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
                    i = mExplorerItemList.update(item, event.getNewItem());
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemChanged(item, i);
                    }
                }
                case ExplorerChangeEvent.CREATE -> {
                    mExplorerItemList.insertAtFront(event.getNewItem());
                    mExplorerAdapter.notifyItemInserted(event.getNewItem(), 0);
                }
                case ExplorerChangeEvent.REMOVE -> {
                    i = mExplorerItemList.remove(item);
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemRemoved(item, i);
                    }
                }
            }
        }
    }

    @Override
    public void onRefresh() {
        mCurrentPageState.scrollY = 0;
        mExplorer.notifyChildrenChanged(mCurrentPageState.page);
        mProjectToolbar.refresh();
    }

    public ScriptFile getCurrentDirectory() {
        return getCurrentPage().toScriptFile();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.rename:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .rename((ExplorerFileItem) mSelectedItem)
                        .subscribe(Observers.emptyObserver());
                break;
            case R.id.delete:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .delete(mSelectedItem.toScriptFile());
                break;
            case R.id.set_as_working_dir:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .setAsWorkingDir(mSelectedItem.toScriptFile());
                break;
            case R.id.run_repeatedly:
                new ScriptLoopDialog(getContext(), mSelectedItem.toScriptFile())
                        .show();
                notifyItemOperated();
                break;
            case R.id.create_shortcut:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .createShortcut(mSelectedItem.toScriptFile());
                break;
            case R.id.open_by_other_apps:
                Scripts.openByOtherApps(mSelectedItem.toScriptFile());
                notifyItemOperated();
                break;
            case R.id.send:
                Scripts.send(getContext(), mSelectedItem.toScriptFile());
                notifyItemOperated();
                break;
            case R.id.timed_task:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .timedTask(mSelectedItem.toScriptFile());
                notifyItemOperated();
                break;
            case R.id.action_build_apk:
                new BuildActivity.IntentBuilder(getContext())
                        .extra(mSelectedItem.getPath())
                        .start();
                notifyItemOperated();
                break;
            case R.id.action_sort_by_date:
                sort(ExplorerItemList.SORT_TYPE_DATE, mDirSortMenuShowing);
                break;
            case R.id.action_sort_by_type:
                sort(ExplorerItemList.SORT_TYPE_TYPE, mDirSortMenuShowing);
                break;
            case R.id.action_sort_by_name:
                sort(ExplorerItemList.SORT_TYPE_NAME, mDirSortMenuShowing);
                break;
            case R.id.action_sort_by_size:
                sort(ExplorerItemList.SORT_TYPE_SIZE, mDirSortMenuShowing);
                break;
            case R.id.reset:
                Observable<ScriptFile> o = Explorers.Providers.workspace()
                        .resetSample(mSelectedItem.toScriptFile());
                if (o == null) {
                    resetFailed();
                } else {
                    o.observeOn(AndroidSchedulers.mainThread())
                            .subscribe(file -> {
                                if (file.exists()) {
                                    resetSucceeded();
                                } else {
                                    resetFailed();
                                }
                            }, Observers.toastMessage());
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private void resetSucceeded() {
        ViewUtils.showSnack(this, R.string.text_reset_succeed);
    }

    private void resetFailed() {
        ViewUtils.showSnack(this, R.string.text_reset_to_initial_content_only_for_assets, true);
    }

    protected void notifyItemOperated() {
        if (mOnItemOperateListener != null) {
            mOnItemOperateListener.onItemOperated(mSelectedItem);
        }
    }

    private void sort(final int sortType, final boolean isDir) {
        setRefreshing(true);
        Callable<ExplorerItemList> explorerItemListCallable = () -> {
            if (isDir) {
                mExplorerItemList.sortItemGroup(sortType);
            } else {
                mExplorerItemList.sortFile(sortType);
            }
            return mExplorerItemList;
        };
        Observable.fromCallable(explorerItemListCallable)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(o -> {
                    notifyDataSetChanged();
                    setRefreshing(false);
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
    }

    protected BindableViewHolder<?> onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            return new ExplorerItemViewHolder(inflater.inflate(R.layout.script_file_list_file, parent, false));
        } else if (viewType == VIEW_TYPE_PAGE) {
            return new ExplorerPageViewHolder(inflater.inflate(R.layout.script_file_list_directory, parent, false));
        } else {
            return new CategoryViewHolder(inflater.inflate(R.layout.script_file_list_category, parent, false));
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
                ((BindableViewHolder<ExplorerPage>) holder).bind(mExplorerItemList.getItemGroup(position - 1), position);
                return;
            }
            ((BindableViewHolder<ExplorerItem>) holder).bind(mExplorerItemList.getItem(position - positionOfCategoryFile - 1), position);
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
            if (!mCurrentPageState.dirsCollapsed) {
                count += mExplorerItemList.groupCount();
            }
            if (!mCurrentPageState.filesCollapsed) {
                count += mExplorerItemList.itemCount();
            }
            return count + 2;
        }
    }

    protected class ExplorerItemViewHolder extends BindableViewHolder<ExplorerItem> {

        @BindView(R.id.name)
        TextView mName;
        @BindView(R.id.first_char)
        FirstCharView mFirstChar;
        @BindView(R.id.desc)
        TextView mDesc;
        @BindView(R.id.more)
        View mOptions;
        @BindView(R.id.edit)
        View mEdit;
        @BindView(R.id.run)
        View mRun;

        private ExplorerItem mExplorerItem;

        ExplorerItemViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(ExplorerItem item, int position) {
            mExplorerItem = item;
            setFirstChar();
            mName.setText(ExplorerViewHelper.getDisplayName(getContext(), item));
            mDesc.setText(PFiles.getHumanReadableSize(item.getSize()));
            mEdit.setVisibility(item.isEditable() ? VISIBLE : GONE);
            mRun.setVisibility(item.isExecutable() ? VISIBLE : GONE);
        }

        private void setFirstChar() {
            mFirstChar.setIconText(ExplorerViewHelper.getIconText(mExplorerItem));
            switch (mExplorerItem.getType()) {
                case JAVASCRIPT, AUTO -> mFirstChar
                        .setIconTextColorNightDay()
                        .setStrokeThemeColor()
                        .setFillThemeColor();

                // @Hint by SuperMonster003 on Aug 26, 2022.
                //  ! These default setters could be placed into its class as an initializer.
                //  ! Even though, code below is still necessary to avoid abnormal icon behaviours.
                default -> mFirstChar
                        .setIconTextColorDayNight()
                        .setStrokeColorDayNight()
                        .setFillTransparent();
            }
        }

        @OnClick(R.id.item)
        void onItemClick() {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(itemView, mExplorerItem);
            }
            notifyItemOperated();
        }

        @OnClick(R.id.run)
        void run() {
            Scripts.run(getContext(), new ScriptFile(mExplorerItem.getPath()));
            notifyItemOperated();
        }

        @OnClick(R.id.edit)
        void edit() {
            Scripts.edit(getContext(), new ScriptFile(mExplorerItem.getPath()));
            notifyItemOperated();
        }

        @OnClick(R.id.more)
        void showOptionMenu() {
            mSelectedItem = mExplorerItem;
            PopupMenu popupMenu = new PopupMenu(getContext(), mOptions);
            popupMenu.inflate(R.menu.menu_script_options);
            Menu menu = popupMenu.getMenu();
            if (!mExplorerItem.isExecutable()) {
                menu.removeItem(R.id.run_repeatedly);
                menu.removeItem(R.id.more);
            }
            if (!mExplorerItem.canDelete()) {
                menu.removeItem(R.id.delete);
            }
            if (!mExplorerItem.canRename()) {
                menu.removeItem(R.id.rename);
            }
            String samplePath = new PFile(getContext().getFilesDir(), SAMPLE_PATH).getPath();
            if (!(mExplorerItem.getPath().startsWith(samplePath))) {
                menu.removeItem(R.id.reset);
            }
            popupMenu.setOnMenuItemClickListener(ExplorerView.this);
            popupMenu.show();
        }
    }

    protected class ExplorerPageViewHolder extends BindableViewHolder<ExplorerPage> {

        @BindView(R.id.name)
        public TextView mName;

        @BindView(R.id.more)
        public View mOptions;

        @BindView(R.id.icon)
        public ImageView mIcon;

        private ExplorerPage mExplorerPage;

        ExplorerPageViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(ExplorerPage data, int position) {
            mName.setText(ExplorerViewHelper.getDisplayName(getContext(), data));
            mIcon.setImageResource(ExplorerViewHelper.getIcon(data));
            mOptions.setVisibility(data instanceof ExplorerSamplePage ? GONE : VISIBLE);
            mExplorerPage = data;
        }

        @OnClick(R.id.item)
        void onItemClick() {
            enterDirectChildPage(mExplorerPage);
        }

        @OnClick(R.id.more)
        void showOptionMenu() {
            mSelectedItem = mExplorerPage;
            PopupMenu popupMenu = new PopupMenu(getContext(), mOptions);
            popupMenu.inflate(R.menu.menu_dir_options);
            popupMenu.setOnMenuItemClickListener(ExplorerView.this);
            popupMenu.show();
        }
    }

    class CategoryViewHolder extends BindableViewHolder<Boolean> {

        @BindView(R.id.title)
        TextView mTitle;

        @BindView(R.id.sort)
        ImageView mSort;

        @BindView(R.id.order)
        ImageView mSortOrder;

        @BindView(R.id.up)
        ImageView mGoUp;

        @BindView(R.id.collapse)
        ImageView mArrow;

        private boolean mIsDir;

        CategoryViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(Boolean isDirCategory, int position) {
            mTitle.setText(isDirCategory ? R.string.text_directory : R.string.text_file);
            mIsDir = isDirCategory;
            if (isDirCategory && canGoUp()) {
                mGoUp.setVisibility(VISIBLE);
            } else {
                mGoUp.setVisibility(GONE);
            }
            if (isDirCategory) {
                mArrow.setRotation(mCurrentPageState.dirsCollapsed ? -90 : 0);
                mSortOrder.setImageResource(mExplorerItemList.isDirSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
            } else {
                mArrow.setRotation(mCurrentPageState.filesCollapsed ? -90 : 0);
                mSortOrder.setImageResource(mExplorerItemList.isFileSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
            }
        }

        @OnClick(R.id.order)
        void changeSortOrder() {
            if (mIsDir) {
                mSortOrder.setImageResource(mExplorerItemList.isDirSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
                mExplorerItemList.setDirSortedAscending(!mExplorerItemList.isDirSortedAscending());
                sort(mExplorerItemList.getDirSortType(), mIsDir);
            } else {
                mSortOrder.setImageResource(mExplorerItemList.isFileSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
                mExplorerItemList.setFileSortedAscending(!mExplorerItemList.isFileSortedAscending());
                sort(mExplorerItemList.getFileSortType(), mIsDir);
            }
        }

        @OnClick(R.id.sort)
        void showSortOptions() {
            PopupMenu popupMenu = new PopupMenu(getContext(), mSort);
            popupMenu.inflate(R.menu.menu_sort_options);
            popupMenu.setOnMenuItemClickListener(ExplorerView.this);
            mDirSortMenuShowing = mIsDir;
            popupMenu.show();

        }

        @OnClick(R.id.up)
        void up() {
            if (canGoUp()) {
                goUp();
            }
        }

        @OnClick(R.id.title_container)
        void collapseOrExpand() {
            if (mIsDir) {
                mCurrentPageState.dirsCollapsed = !mCurrentPageState.dirsCollapsed;
            } else {
                mCurrentPageState.filesCollapsed = !mCurrentPageState.filesCollapsed;
            }
            notifyDataSetChanged();
        }
    }

    private static class ExplorerPageState {

        ExplorerPage page;

        boolean dirsCollapsed;

        boolean filesCollapsed;

        int scrollY;

        ExplorerPageState() {
        }

        ExplorerPageState(ExplorerPage page) {
            this.page = page;
        }
    }
}
