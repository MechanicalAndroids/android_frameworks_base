/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Per article 5 of the Apache 2.0 License, some modifications to this code
 * were made by the OmniROM Project.
 *
 * Modifications Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Per article 5 of the Apache 2.0 License, some modifications to this code
 * were made by the Oneplus Project.
 *
 * Modifications Copyright (C) 2015 The Oneplus Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.android.documentsui;

import static com.android.documentsui.DirectoryFragment.ANIM_DOWN;
import static com.android.documentsui.DirectoryFragment.ANIM_NONE;
import static com.android.documentsui.DirectoryFragment.ANIM_SIDE;
import static com.android.documentsui.DirectoryFragment.ANIM_UP;
import static com.android.documentsui.DocumentsActivity.State.ACTION_CREATE;
import static com.android.documentsui.DocumentsActivity.State.ACTION_GET_CONTENT;
import static com.android.documentsui.DocumentsActivity.State.ACTION_MANAGE;
import static com.android.documentsui.DocumentsActivity.State.ACTION_OPEN;
import static com.android.documentsui.DocumentsActivity.State.ACTION_STANDALONE;
import static com.android.documentsui.DocumentsActivity.State.ACTION_OPEN_TREE;
import static com.android.documentsui.DocumentsActivity.State.MODE_GRID;
import static com.android.documentsui.DocumentsActivity.State.MODE_LIST;

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Root;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.documentsui.RecentsProvider.RecentColumns;
import com.android.documentsui.RecentsProvider.ResumeColumns;
import com.android.documentsui.model.DocumentInfo;
import com.android.documentsui.model.DocumentStack;
import com.android.documentsui.model.DurableUtils;
import com.android.documentsui.model.RootInfo;
import com.google.common.collect.Maps;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

public class DocumentsActivity extends Activity {
    public static final String TAG = "Documents";

    private static final String EXTRA_STATE = "state";

    private static final int CODE_FORWARD = 42;

    private static final long MEGA_BYTE = 1024L * 1024L;

    private static ProgressDialog mProgressDialog;

    private boolean mShowAsDialog;

    private SearchView mSearchView;

    private Toolbar mToolbar;
    private View mBottomToolbar;
    private Spinner mToolbarStack;

    private Toolbar mRootsToolbar;

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private View mRootsDrawer;

    private DirectoryContainerView mDirectoryContainer;

    private boolean mIgnoreNextNavigation;
    private boolean mIgnoreNextClose;
    private boolean mIgnoreNextCollapse;

    private boolean mSearchExpanded;

    private RootsCache mRoots;
    private State mState;

    private List<DocumentInfo> mClipboardFiles;
    /* true if copy, false if cut */
    private boolean mClipboardIsCopy;

    private List<DocumentInfo> mCopyFiles;
    private ArrayList<DirectoryLoaderCallback> mCopyCallbacks;
    private int mLoaderId;

    private StorageManager mStorageManager;

    private final Object mRootsLock = new Object();
    private HashMap<String, File> mIdToPath;

    private Handler mHandler;
    private ArrayList<Dialog> mDialogs;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mRoots = DocumentsApplication.getRootsCache(this);

        mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);

        mHandler = new Handler();

        mIdToPath = Maps.newHashMap();
        mDialogs = new ArrayList<Dialog>();
        updateVolumes();

        setResult(Activity.RESULT_CANCELED);
        setContentView(R.layout.activity);

        final Context context = this;
        final Resources res = getResources();
        mShowAsDialog = res.getBoolean(R.bool.show_as_dialog);

        if (mShowAsDialog) {
            // Strongly define our horizontal dimension; we leave vertical as
            // WRAP_CONTENT so that system resizes us when IME is showing.
            final WindowManager.LayoutParams a = getWindow().getAttributes();

            final Point size = new Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            a.width = (int) res.getFraction(R.dimen.dialog_width, size.x, size.x);

            getWindow().setAttributes(a);

        } else {
            // Non-dialog means we have a drawer
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    R.drawable.ic_hamburger, R.string.drawer_open, R.string.drawer_close);

            mDrawerLayout.setDrawerListener(mDrawerListener);

            mRootsDrawer = findViewById(R.id.drawer_roots);
        }

        mDirectoryContainer = (DirectoryContainerView) findViewById(R.id.container_directory);

        if (icicle != null) {
            mState = icicle.getParcelable(EXTRA_STATE);
        } else {
            buildDefaultState();
        }

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitleTextAppearance(context,
                android.R.style.TextAppearance_DeviceDefault_Widget_ActionBar_Title);

        mBottomToolbar = findViewById(R.id.bottom_toolbar);

        mToolbarStack = (Spinner) findViewById(R.id.stack);
        mToolbarStack.setOnItemSelectedListener(mStackListener);

        mRootsToolbar = (Toolbar) findViewById(R.id.roots_toolbar);
        if (mRootsToolbar != null) {
            mRootsToolbar.setTitleTextAppearance(context,
                    android.R.style.TextAppearance_DeviceDefault_Widget_ActionBar_Title);
        }

        setActionBar(mToolbar);

        // Hide roots when we're managing a specific root
        if (mState.action == ACTION_MANAGE) {
            if (mShowAsDialog) {
                findViewById(R.id.container_roots).setVisibility(View.GONE);
            } else {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }

        if (mState.action == ACTION_CREATE) {
            final String mimeType = getIntent().getType();
            final String title = getIntent().getStringExtra(Intent.EXTRA_TITLE);
            SaveFragment.show(getFragmentManager(), mimeType, title);
        } else if (mState.action == ACTION_OPEN_TREE) {
            PickFragment.show(getFragmentManager());
        }

        if (mState.action == ACTION_GET_CONTENT) {
            final Intent moreApps = new Intent(getIntent());
            moreApps.setComponent(null);
            moreApps.setPackage(null);
            RootsFragment.show(getFragmentManager(), moreApps);
        } else if (mState.action == ACTION_OPEN || mState.action == ACTION_CREATE
                || mState.action == ACTION_OPEN_TREE || mState.action == ACTION_STANDALONE) {
            RootsFragment.show(getFragmentManager(), null);
        }

        if (!mState.restored) {
            if (mState.action == ACTION_MANAGE) {
                final Uri rootUri = getIntent().getData();
                if (rootUri != null) {
                    new RestoreRootTask(rootUri).executeOnExecutor(getCurrentExecutor());
                } else {
                    new RestoreStackTask().execute();
                }
            } else {
                new RestoreStackTask().execute();
            }
        } else {
            onCurrentDirectoryChanged(ANIM_NONE);
        }
        if (mState.copyFiles > 0 && mState.copySize > 0 && mProgressDialog != null) {
            showProgressDialog(mState.copyFiles, mState.copySize, mState.copyProgress);
        }
    }

    public void updateVolumes() {
        synchronized (mRootsLock) {
            updateVolumesLocked();
        }
    }

    private void updateVolumesLocked() {
        mIdToPath.clear();

        boolean switchToRecents = false;
        final RootInfo currentRoot = mState == null ? null : getCurrentRoot();
        final StorageVolume[] volumes = mStorageManager.getVolumeList();
        for (StorageVolume volume : volumes) {
            final boolean mounted = Environment.MEDIA_MOUNTED.equals(volume.getState())
                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(volume.getState());
            if (!mounted) {
                if (currentRoot != null && currentRoot.rootId != null &&
                        currentRoot.rootId.equals(volume.getUuid())) {
                    switchToRecents = true;
                }
                continue;
            }

            final String rootId;
            if (volume.isPrimary() && volume.isEmulated()) {
                rootId = "primary";
            } else if (volume.getUuid() != null) {
                rootId = volume.getUuid();
            } else {
                continue;
            }

            if (mIdToPath.containsKey(rootId)) {
                continue;
            }

            final File path = volume.getPathFile();
            mIdToPath.put(rootId, path);
            Log.d(TAG, "Found volume path: " + rootId + ":" + path);
        }
        if (switchToRecents) {
            dismissAllDialogs();
            onRootPicked(mRoots.getRecentsRoot(), false);
        }

    }

    private void buildDefaultState() {
        mState = new State();

        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_OPEN_DOCUMENT.equals(action)) {
            mState.action = ACTION_OPEN;
        } else if (Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            mState.action = ACTION_CREATE;
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            mState.action = ACTION_GET_CONTENT;
        } else if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) {
            mState.action = ACTION_OPEN_TREE;
        } else if (DocumentsContract.ACTION_MANAGE_ROOT.equals(action)) {
            mState.action = ACTION_MANAGE;
        } else if (Intent.ACTION_MAIN.equals(action)) {
            mState.action = ACTION_STANDALONE;
        }

        if (mState.action == ACTION_OPEN || mState.action == ACTION_GET_CONTENT) {
            mState.allowMultiple = intent.getBooleanExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE, false);
        } else if (mState.action == ACTION_STANDALONE) {
            mState.allowMultiple = true;
        }

        if (mState.action == ACTION_MANAGE) {
            mState.acceptMimes = new String[] { "*/*" };
            mState.allowMultiple = true;
        } else if (intent.hasExtra(Intent.EXTRA_MIME_TYPES)) {
            mState.acceptMimes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        } else {
            mState.acceptMimes = new String[] { intent.getType() };
        }

        mState.localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false);
        mState.forceAdvanced = intent.getBooleanExtra(DocumentsContract.EXTRA_SHOW_ADVANCED, false);
        mState.showAdvanced = mState.forceAdvanced
                | LocalPreferences.getDisplayAdvancedDevices(this);

        if (mState.action == ACTION_MANAGE) {
            mState.showSize = true;
        } else {
            mState.showSize = LocalPreferences.getDisplayFileSize(this);
        }
    }

    private class RestoreRootTask extends AsyncTask<Void, Void, RootInfo> {
        private Uri mRootUri;

        public RestoreRootTask(Uri rootUri) {
            mRootUri = rootUri;
        }

        @Override
        protected RootInfo doInBackground(Void... params) {
            final String rootId = DocumentsContract.getRootId(mRootUri);
            return mRoots.getRootOneshot(mRootUri.getAuthority(), rootId);
        }

        @Override
        protected void onPostExecute(RootInfo root) {
            if (isDestroyed()) return;
            mState.restored = true;

            if (root != null) {
                onRootPicked(root, true);
            } else {
                Log.w(TAG, "Failed to find root: " + mRootUri);
                finish();
            }
        }
    }

    private class RestoreStackTask extends AsyncTask<Void, Void, Void> {
        private volatile boolean mRestoredStack;
        private volatile boolean mExternal;

        @Override
        protected Void doInBackground(Void... params) {
            // Restore last stack for calling package
            final String packageName = getCallingPackageMaybeExtra();
            final Cursor cursor = getContentResolver()
                    .query(RecentsProvider.buildResume(packageName), null, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    mExternal = cursor.getInt(cursor.getColumnIndex(ResumeColumns.EXTERNAL)) != 0;
                    final byte[] rawStack = cursor.getBlob(
                            cursor.getColumnIndex(ResumeColumns.STACK));
                    DurableUtils.readFromArray(rawStack, mState.stack);
                    mRestoredStack = true;
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to resume: " + e);
            } finally {
                IoUtils.closeQuietly(cursor);
            }

            if (mRestoredStack) {
                // Update the restored stack to ensure we have freshest data
                final Collection<RootInfo> matchingRoots = mRoots.getMatchingRootsBlocking(mState);
                try {
                    mState.stack.updateRoot(matchingRoots);
                    mState.stack.updateDocuments(getContentResolver());
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "Failed to restore stack: " + e);
                    mState.stack.reset();
                    mRestoredStack = false;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (isDestroyed()) return;
            mState.restored = true;

            // Show drawer when no stack restored, but only when requesting
            // non-visual content. However, if we last used an external app,
            // drawer is always shown.

            boolean showDrawer = false;
            if (!mRestoredStack) {
                showDrawer = true;
            }
            if (MimePredicate.mimeMatches(MimePredicate.VISUAL_MIMES, mState.acceptMimes)) {
                showDrawer = false;
            }
            if (mExternal && mState.action == ACTION_GET_CONTENT) {
                showDrawer = true;
            }

            if (showDrawer) {
                setRootsDrawerOpen(true);
            }

            onCurrentDirectoryChanged(ANIM_NONE);
        }
    }

    private DrawerListener mDrawerListener = new DrawerListener() {
        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            mDrawerToggle.onDrawerOpened(drawerView);
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            mDrawerToggle.onDrawerClosed(drawerView);
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            mDrawerToggle.onDrawerStateChanged(newState);
        }
    };

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
        updateActionBar();
    }

    public void setRootsDrawerOpen(boolean open) {
        if (!mShowAsDialog) {
            if (open) {
                mDrawerLayout.openDrawer(mRootsDrawer);
            } else {
                mDrawerLayout.closeDrawer(mRootsDrawer);
            }
        }
    }

    private boolean isRootsDrawerOpen() {
        if (mShowAsDialog) {
            return false;
        } else {
            return mDrawerLayout.isDrawerOpen(mRootsDrawer);
        }
    }

    public void updateActionBar() {
        if (mRootsToolbar != null) {
            if (mState.action == ACTION_OPEN || mState.action == ACTION_GET_CONTENT
                    || mState.action == ACTION_OPEN_TREE) {
                mRootsToolbar.setTitle(R.string.title_open);
            } else if (mState.action == ACTION_CREATE) {
                mRootsToolbar.setTitle(R.string.title_save);
            } else if (mState.action == ACTION_STANDALONE) {
                mRootsToolbar.setTitle(R.string.title_standalone);
            }
        }

        final RootInfo root = getCurrentRoot();
        final boolean showRootIcon = mShowAsDialog || (mState.action == ACTION_MANAGE);
        if (showRootIcon) {
            mToolbar.setNavigationIcon(
                    root != null ? root.loadToolbarIcon(mToolbar.getContext()) : null);
            mToolbar.setNavigationContentDescription(R.string.drawer_open);
            mToolbar.setNavigationOnClickListener(null);
        } else {
            mToolbar.setNavigationIcon(R.drawable.ic_hamburger);
            mToolbar.setNavigationContentDescription(R.string.drawer_open);
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setRootsDrawerOpen(true);
                }
            });
        }

        if (mSearchExpanded) {
            mToolbar.setTitle(null);
            mToolbarStack.setVisibility(View.GONE);
            mToolbarStack.setAdapter(null);
        } else {
            if (mState.stack.size() <= 1) {
                mToolbar.setTitle(root.title);
                mToolbarStack.setVisibility(View.GONE);
                mToolbarStack.setAdapter(null);
            } else {
                mToolbar.setTitle(null);
                mToolbarStack.setVisibility(View.VISIBLE);
                mToolbarStack.setAdapter(mStackAdapter);

                mIgnoreNextNavigation = true;
                mToolbarStack.setSelection(mStackAdapter.getCount() - 1);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity, menu);

        // Most actions are visible when showing as dialog
        if (mShowAsDialog) {
            for (int i = 0; i < menu.size(); i++) {
                final MenuItem item = menu.getItem(i);
                switch (item.getItemId()) {
                    case R.id.menu_advanced:
                    case R.id.menu_file_size:
                        break;
                    default:
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
            }
        }

        final MenuItem searchMenu = menu.findItem(R.id.menu_search);
        mSearchView = (SearchView) searchMenu.getActionView();
        mSearchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mSearchExpanded = true;
                mState.currentSearch = query;
                mSearchView.clearFocus();
                onCurrentDirectoryChanged(ANIM_NONE);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        searchMenu.setOnActionExpandListener(new OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchExpanded = true;
                updateActionBar();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchExpanded = false;
                if (mIgnoreNextCollapse) {
                    mIgnoreNextCollapse = false;
                    return true;
                }

                mState.currentSearch = null;
                onCurrentDirectoryChanged(ANIM_NONE);
                return true;
            }
        });

        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                mSearchExpanded = false;
                if (mIgnoreNextClose) {
                    mIgnoreNextClose = false;
                    return false;
                }

                mState.currentSearch = null;
                onCurrentDirectoryChanged(ANIM_NONE);
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final FragmentManager fm = getFragmentManager();

        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();

        final MenuItem createDir = menu.findItem(R.id.menu_create_dir);
        final MenuItem search = menu.findItem(R.id.menu_search);
        final MenuItem sort = menu.findItem(R.id.menu_sort);
        final MenuItem sortSize = menu.findItem(R.id.menu_sort_size);
        final MenuItem grid = menu.findItem(R.id.menu_grid);
        final MenuItem list = menu.findItem(R.id.menu_list);
        final MenuItem advanced = menu.findItem(R.id.menu_advanced);
        final MenuItem fileSize = menu.findItem(R.id.menu_file_size);
        final MenuItem paste = menu.findItem(R.id.menu_paste);

        // Paste is visible only if we have files in the clipboard, and if
        // we can paste in this directory
        paste.setVisible(mClipboardFiles != null && mClipboardFiles.size() > 0
                && cwd != null && cwd.isPasteSupported());

        sort.setVisible(cwd != null);
        grid.setVisible(mState.derivedMode != MODE_GRID);
        list.setVisible(mState.derivedMode != MODE_LIST);

        if (mState.currentSearch != null) {
            // Search uses backend ranking; no sorting
            sort.setVisible(false);

            search.expandActionView();

            mSearchView.setIconified(false);
            mSearchView.clearFocus();
            mSearchView.setQuery(mState.currentSearch, false);
        } else {
            mIgnoreNextClose = true;
            mSearchView.setIconified(true);
            mSearchView.clearFocus();

            mIgnoreNextCollapse = true;
            search.collapseActionView();
        }

        // Only sort by size when visible
        sortSize.setVisible(mState.showSize);

        boolean fileSizeVisible = mState.action != ACTION_MANAGE;
        final boolean searchVisible;
        if (mState.action == ACTION_CREATE || mState.action == ACTION_OPEN_TREE
                || mState.action == ACTION_STANDALONE) {
            createDir.setVisible(cwd != null && cwd.isCreateSupported());
            searchVisible = false;

            // No display options in recent directories
            if (cwd == null) {
                grid.setVisible(false);
                list.setVisible(false);
                fileSizeVisible = false;
            }

            if (mState.action == ACTION_CREATE) {
                SaveFragment.get(fm).setSaveEnabled(cwd != null && cwd.isCreateSupported());
            }
        } else {
            createDir.setVisible(false);

            searchVisible = root != null
                    && ((root.flags & Root.FLAG_SUPPORTS_SEARCH) != 0);
        }

        // TODO: close any search in-progress when hiding
        search.setVisible(searchVisible);

        advanced.setTitle(LocalPreferences.getDisplayAdvancedDevices(this)
                ? R.string.menu_advanced_hide : R.string.menu_advanced_show);
        fileSize.setTitle(LocalPreferences.getDisplayFileSize(this)
                ? R.string.menu_file_size_hide : R.string.menu_file_size_show);

        advanced.setVisible(mState.action != ACTION_MANAGE);
        fileSize.setVisible(fileSizeVisible);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        final int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.menu_create_dir) {
            CreateDirectoryFragment.show(getFragmentManager());
            return true;
        } else if (id == R.id.menu_paste) {
            onPasteRequested();
            return true;
        } else if (id == R.id.menu_search) {
            return false;
        } else if (id == R.id.menu_sort_name) {
            setUserSortOrder(State.SORT_ORDER_DISPLAY_NAME);
            return true;
        } else if (id == R.id.menu_sort_date) {
            setUserSortOrder(State.SORT_ORDER_LAST_MODIFIED);
            return true;
        } else if (id == R.id.menu_sort_size) {
            setUserSortOrder(State.SORT_ORDER_SIZE);
            return true;
        } else if (id == R.id.menu_grid) {
            setUserMode(State.MODE_GRID);
            return true;
        } else if (id == R.id.menu_list) {
            setUserMode(State.MODE_LIST);
            return true;
        } else if (id == R.id.menu_advanced) {
            setDisplayAdvancedDevices(!LocalPreferences.getDisplayAdvancedDevices(this));
            return true;
        } else if (id == R.id.menu_file_size) {
            setDisplayFileSize(!LocalPreferences.getDisplayFileSize(this));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    protected void addDialog(Dialog dialog) {
        mDialogs.add(dialog);
    }

    protected void removeDialog(Dialog dialog) {
        mDialogs.remove(dialog);
    }

    private void dismissAllDialogs() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                for (Dialog dialog : mDialogs) {
                    if (dialog != null && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }
                mDialogs.clear();
            }
        });
    }

    private void setDisplayAdvancedDevices(boolean display) {
        LocalPreferences.setDisplayAdvancedDevices(this, display);
        mState.showAdvanced = mState.forceAdvanced | display;
        RootsFragment.get(getFragmentManager()).onDisplayStateChanged();
        invalidateOptionsMenu();
    }

    private void setDisplayFileSize(boolean display) {
        LocalPreferences.setDisplayFileSize(this, display);
        mState.showSize = display;
        DirectoryFragment.get(getFragmentManager()).onDisplayStateChanged();
        invalidateOptionsMenu();
    }

    /**
     * Update UI to reflect internal state changes not from user.
     */
    public void onStateChanged() {
        invalidateOptionsMenu();
    }

    /**
     * Set state sort order based on explicit user action.
     */
    private void setUserSortOrder(int sortOrder) {
        mState.userSortOrder = sortOrder;
        DirectoryFragment.get(getFragmentManager()).onUserSortOrderChanged();
    }

    /**
     * Set state mode based on explicit user action.
     */
    private void setUserMode(int mode) {
        mState.userMode = mode;
        DirectoryFragment.get(getFragmentManager()).onUserModeChanged();
    }

    public void setPending(boolean pending) {
        final SaveFragment save = SaveFragment.get(getFragmentManager());
        if (save != null) {
            save.setPending(pending);
        }
    }

    @Override
    public void onBackPressed() {
        if (!mState.stackTouched) {
            super.onBackPressed();
            return;
        }

        final int size = mState.stack.size();
        if (size > 1) {
            mState.stack.pop();
            onCurrentDirectoryChanged(ANIM_UP);
        } else if (size == 1 && !isRootsDrawerOpen()) {
            // TODO: open root drawer once we can capture back key
            super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(EXTRA_STATE, mState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
    }

    private BaseAdapter mStackAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mState.stack.size();
        }

        @Override
        public DocumentInfo getItem(int position) {
            return mState.stack.get(mState.stack.size() - position - 1);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_subdir_title, parent, false);
            }

            final TextView title = (TextView) convertView.findViewById(android.R.id.title);
            final DocumentInfo doc = getItem(position);

            if (position == 0) {
                final RootInfo root = getCurrentRoot();
                title.setText(root.title);
            } else {
                title.setText(doc.displayName);
            }

            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_subdir, parent, false);
            }

            final ImageView subdir = (ImageView) convertView.findViewById(R.id.subdir);
            final TextView title = (TextView) convertView.findViewById(android.R.id.title);
            final DocumentInfo doc = getItem(position);

            if (position == 0) {
                final RootInfo root = getCurrentRoot();
                title.setText(root.title);
                subdir.setVisibility(View.GONE);
            } else {
                title.setText(doc.displayName);
                subdir.setVisibility(View.VISIBLE);
            }

            return convertView;
        }
    };

    private OnItemSelectedListener mStackListener = new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (mIgnoreNextNavigation) {
                mIgnoreNextNavigation = false;
                return;
            }

            while (mState.stack.size() > position + 1) {
                mState.stackTouched = true;
                mState.stack.pop();
            }
            onCurrentDirectoryChanged(ANIM_UP);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Ignored
        }
    };

    public RootInfo getCurrentRoot() {
        if (mState.stack.root != null) {
            return mState.stack.root;
        } else {
            return mRoots.getRecentsRoot();
        }
    }

    public DocumentInfo getCurrentDirectory() {
        return mState.stack.peek();
    }

    private String getCallingPackageMaybeExtra() {
        final String extra = getIntent().getStringExtra(DocumentsContract.EXTRA_PACKAGE_NAME);
        return (extra != null) ? extra : getCallingPackage();
    }

    public Executor getCurrentExecutor() {
        final DocumentInfo cwd = getCurrentDirectory();
        if (cwd != null && cwd.authority != null) {
            return ProviderExecutor.forAuthority(cwd.authority);
        } else {
            return AsyncTask.THREAD_POOL_EXECUTOR;
        }
    }

    public State getDisplayState() {
        return mState;
    }

    protected View getBottomToolbar() {
        return mBottomToolbar;
    }

    private void onCurrentDirectoryChanged(int anim) {
        final FragmentManager fm = getFragmentManager();
        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();

        mDirectoryContainer.setDrawDisappearingFirst(anim == ANIM_DOWN);

        if (cwd == null) {
            // No directory means recents
            if (mState.action == ACTION_CREATE || mState.action == ACTION_OPEN_TREE) {
                RecentsCreateFragment.show(fm);
            } else {
                DirectoryFragment.showRecentsOpen(fm, anim, mBottomToolbar);

                // Start recents in grid when requesting visual things
                final boolean visualMimes = MimePredicate.mimeMatches(
                        MimePredicate.VISUAL_MIMES, mState.acceptMimes);
                mState.userMode = visualMimes ? MODE_GRID : MODE_LIST;
                mState.derivedMode = mState.userMode;
            }
        } else {
            if (mState.currentSearch != null) {
                // Ongoing search
                DirectoryFragment.showSearch(fm, root, mState.currentSearch, anim, mBottomToolbar);
            } else {
                // Normal boring directory
                DirectoryFragment.showNormal(fm, root, cwd, anim, mBottomToolbar);
            }
        }

        // Forget any replacement target
        if (mState.action == ACTION_CREATE || mState.action == ACTION_STANDALONE) {
            final SaveFragment save = SaveFragment.get(fm);
            if (save != null) {
                save.setReplaceTarget(null);
            }
        }

        if (mState.action == ACTION_OPEN_TREE) {
            final PickFragment pick = PickFragment.get(fm);
            if (pick != null) {
                final CharSequence displayName = (mState.stack.size() <= 1) ? root.title
                        : cwd.displayName;
                if (displayName != null) {
                    pick.setPickTarget(cwd, displayName);
                }
            }
        }

        final RootsFragment roots = RootsFragment.get(fm);
        if (roots != null) {
            roots.onCurrentRootChanged();
        }

        updateActionBar();
        invalidateOptionsMenu();
        dumpStack();
    }

    public void onStackPicked(DocumentStack stack) {
        try {
            // Update the restored stack to ensure we have freshest data
            stack.updateDocuments(getContentResolver());

            mState.stack = stack;
            mState.stackTouched = true;
            onCurrentDirectoryChanged(ANIM_SIDE);

        } catch (FileNotFoundException e) {
            Log.w(TAG, "Failed to restore stack: " + e);
        }
    }

    public void onRootPicked(RootInfo root, boolean closeDrawer) {
        // Clear entire backstack and start in new root
        mState.stack.root = root;
        mState.stack.clear();
        mState.stackTouched = true;

        if (!mRoots.isRecentsRoot(root)) {
            new PickRootTask(root).executeOnExecutor(getCurrentExecutor());
        } else {
            onCurrentDirectoryChanged(ANIM_SIDE);
        }

        if (closeDrawer) {
            setRootsDrawerOpen(false);
        }
    }

    private class PickRootTask extends AsyncTask<Void, Void, DocumentInfo> {
        private RootInfo mRoot;

        public PickRootTask(RootInfo root) {
            mRoot = root;
        }

        @Override
        protected DocumentInfo doInBackground(Void... params) {
            try {
                final Uri uri = DocumentsContract.buildDocumentUri(
                        mRoot.authority, mRoot.documentId);
                return DocumentInfo.fromUri(getContentResolver(), uri);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Failed to find root", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(DocumentInfo result) {
            if (result != null) {
                mState.stack.push(result);
                mState.stackTouched = true;
                onCurrentDirectoryChanged(ANIM_SIDE);
            }
        }
    }

    public void onAppPicked(ResolveInfo info) {
        final Intent intent = new Intent(getIntent());
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.setComponent(new ComponentName(
                info.activityInfo.applicationInfo.packageName, info.activityInfo.name));
        startActivityForResult(intent, CODE_FORWARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult() code=" + resultCode);

        // Only relay back results when not canceled; otherwise stick around to
        // let the user pick another app/backend.
        if (requestCode == CODE_FORWARD && resultCode != RESULT_CANCELED) {

            // Remember that we last picked via external app
            final String packageName = getCallingPackageMaybeExtra();
            final ContentValues values = new ContentValues();
            values.put(ResumeColumns.EXTERNAL, 1);
            getContentResolver().insert(RecentsProvider.buildResume(packageName), values);

            // Pass back result to original caller
            setResult(resultCode, data);
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void onDocumentPicked(DocumentInfo doc) {
        final FragmentManager fm = getFragmentManager();
        if (doc.isDirectory()) {
            mState.stack.push(doc);
            mState.stackTouched = true;
            onCurrentDirectoryChanged(ANIM_DOWN);
        } else if (mState.action == ACTION_OPEN || mState.action == ACTION_GET_CONTENT) {
            // Explicit file picked, return
            new ExistingFinishTask(doc.derivedUri).executeOnExecutor(getCurrentExecutor());
        } else if (mState.action == ACTION_CREATE) {
            // Replace selected file
            SaveFragment.get(fm).setReplaceTarget(doc);
        } else if (mState.action == ACTION_MANAGE) {
            // First try managing the document; we expect manager to filter
            // based on authority, so we don't grant.
            final Intent manage = new Intent(DocumentsContract.ACTION_MANAGE_DOCUMENT);
            manage.setData(doc.derivedUri);

            try {
                startActivity(manage);
            } catch (ActivityNotFoundException ex) {
                // Fall back to viewing
                final Intent view = new Intent(Intent.ACTION_VIEW);
                view.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                view.setData(doc.derivedUri);

                try {
                    startActivity(view);
                } catch (ActivityNotFoundException ex2) {
                    Toast.makeText(this, R.string.toast_no_application, Toast.LENGTH_SHORT).show();
                }
            }
        } else if (mState.action == ACTION_STANDALONE) {
            final Intent view = new Intent(Intent.ACTION_VIEW);
            view.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            view.setData(doc.derivedUri);

            try {
                startActivity(view);
            } catch (ActivityNotFoundException ex2) {
                String path = DocumentsContract.getPathDocument(getContentResolver(), doc.derivedUri);
                if (path != null) {
                    File file = new File(path);
                    view.setDataAndType(Uri.fromFile(file), doc.mimeType);
                    try {
                        startActivity(view);
                    } catch (ActivityNotFoundException ex3) {
                        Toast.makeText(this, R.string.toast_no_application, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.toast_no_application, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void onDocumentRename(DocumentInfo doc) {
        RenameDocumentFragment.show(getFragmentManager(), doc);
    }

    public void onDocumentsPicked(List<DocumentInfo> docs) {
        if (mState.action == ACTION_OPEN || mState.action == ACTION_GET_CONTENT) {
            final int size = docs.size();
            final Uri[] uris = new Uri[size];
            for (int i = 0; i < size; i++) {
                uris[i] = docs.get(i).derivedUri;
            }
            new ExistingFinishTask(uris).executeOnExecutor(getCurrentExecutor());
        }
    }

    public void setClipboardDocuments(List<DocumentInfo> docs, boolean copy) {
        mClipboardFiles = docs;
        mClipboardIsCopy = copy;
        final Resources r = getResources();
        Toast.makeText(this,
            r.getQuantityString(R.plurals.files_copied, docs.size(), docs.size()),
            Toast.LENGTH_SHORT).show();

        // Update the action bar buttons
        invalidateOptionsMenu();
    }

    public void removeFromClipboard(DocumentInfo doc) {
        if (mClipboardFiles == null) {
            return;
        }
        for (DocumentInfo d : mClipboardFiles) {
            if (d.authority.equals(doc.authority) && d.documentId.equals(doc.documentId)) {
                mClipboardFiles.remove(d);
                return;
            }
        }
    }

    public void onPasteRequested() {
        if (mClipboardFiles == null) {
            return;
        }

        // Add all the things!!!
        mCopyFiles = new ArrayList<DocumentInfo>();
        mCopyCallbacks = new ArrayList<DirectoryLoaderCallback>();
        mLoaderId = 43;
        boolean hasFolders = false;
        for (DocumentInfo doc : mClipboardFiles) {
            hasFolders = hasFolders || doc.isDirectory();
            mCopyFiles.add(doc);
            addRecursively(doc);
        }
        if (!hasFolders) {
            startPaste();
        }
    }

    public void onSaveRequested(DocumentInfo replaceTarget) {
        new ExistingFinishTask(replaceTarget.derivedUri).executeOnExecutor(getCurrentExecutor());
    }

    public void onSaveRequested(String mimeType, String displayName) {
        new CreateFinishTask(mimeType, displayName).executeOnExecutor(getCurrentExecutor());
    }

    public void onPickRequested(DocumentInfo pickTarget) {
        final Uri viaUri = DocumentsContract.buildTreeDocumentUri(pickTarget.authority,
                pickTarget.documentId);
        new PickFinishTask(viaUri).executeOnExecutor(getCurrentExecutor());
    }

    private void startPaste() {

        // Run the corresponding asynctask
        new CopyOrCutFilesTask(mCopyFiles.toArray(new DocumentInfo[0])).executeOnExecutor(
                getCurrentExecutor());

        // Clear the copy buffer
        mCopyFiles = null;
        mCopyCallbacks = null;
    }

    private void addRecursively(final DocumentInfo parent) {
        if (parent.isDirectory()) {
            final DirectoryLoaderCallback callback = new DirectoryLoaderCallback(mLoaderId, parent,
                    new DirectoryLoaderListener() {

                        @Override
                        public void onDirectoryLoaderFinished(DirectoryLoaderCallback callback,
                                ArrayList<DocumentInfo> docs) {
                            synchronized (mCopyCallbacks) {
                                getLoaderManager().destroyLoader(callback.getId());
                                mCopyCallbacks.remove(callback);
                                for (DocumentInfo doc : docs) {
                                    doc.parent = parent;
                                    mCopyFiles.add(doc);
                                    addRecursively(doc);
                                }
                                if (mCopyCallbacks.size() == 0) {
                                    startPaste();
                                }
                            }
                        }

            });
            mLoaderId++;
            mCopyCallbacks.add(callback);
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    getLoaderManager().restartLoader(callback.getId(), null, callback);
                }
            });
        }
    }

    private void saveStackBlocking() {
        final ContentResolver resolver = getContentResolver();
        final ContentValues values = new ContentValues();

        final byte[] rawStack = DurableUtils.writeToArrayOrNull(mState.stack);
        if (mState.action == ACTION_CREATE || mState.action == ACTION_OPEN_TREE) {
            // Remember stack for last create
            values.clear();
            values.put(RecentColumns.KEY, mState.stack.buildKey());
            values.put(RecentColumns.STACK, rawStack);
            resolver.insert(RecentsProvider.buildRecent(), values);
        }

        // Remember location for next app launch
        final String packageName = getCallingPackageMaybeExtra();
        values.clear();
        values.put(ResumeColumns.STACK, rawStack);
        values.put(ResumeColumns.EXTERNAL, 0);
        resolver.insert(RecentsProvider.buildResume(packageName), values);
    }

    private void onFinished(Uri... uris) {
        Log.d(TAG, "onFinished() " + Arrays.toString(uris));

        final Intent intent = new Intent();
        if (uris.length == 1) {
            intent.setData(uris[0]);
        } else if (uris.length > 1) {
            final ClipData clipData = new ClipData(
                    null, mState.acceptMimes, new ClipData.Item(uris[0]));
            for (int i = 1; i < uris.length; i++) {
                clipData.addItem(new ClipData.Item(uris[i]));
            }
            intent.setClipData(clipData);
        }

        if (mState.action == ACTION_GET_CONTENT) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (mState.action == ACTION_OPEN_TREE) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        } else {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }

        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    public void copyFile(Uri input, Uri output) throws IOException {
        OutputStream os = getContentResolver().openOutputStream(output);
        InputStream is = getContentResolver().openInputStream(input);

        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    private class CreateFinishTask extends AsyncTask<Void, Void, Uri> {
        private final String mMimeType;
        private final String mDisplayName;

        public CreateFinishTask(String mimeType, String displayName) {
            mMimeType = mimeType;
            mDisplayName = displayName;
        }

        @Override
        protected void onPreExecute() {
            setPending(true);
        }

        @Override
        protected Uri doInBackground(Void... params) {
            final ContentResolver resolver = getContentResolver();
            final DocumentInfo cwd = getCurrentDirectory();

            ContentProviderClient client = null;
            Uri childUri = null;
            try {
                client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        resolver, cwd.derivedUri.getAuthority());
                childUri = DocumentsContract.createDocument(
                        client, cwd.derivedUri, mMimeType, mDisplayName);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to create document", e);
            } finally {
                ContentProviderClient.releaseQuietly(client);
            }

            if (childUri != null) {
                saveStackBlocking();
            }

            return childUri;
        }

        @Override
        protected void onPostExecute(Uri result) {
            if (result != null) {
                onFinished(result);
            } else {
                Toast.makeText(DocumentsActivity.this, R.string.save_error, Toast.LENGTH_SHORT)
                        .show();
            }

            setPending(false);
        }
    }

    private void showProgressDialog(int files, long size, float progress) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        boolean showMegas = size > 5 * MEGA_BYTE;
        mState.copyFiles = files;
        mState.copySize = size;
        mState.copyProgress = progress;
        mProgressDialog = new ProgressDialog(DocumentsActivity.this);
        mProgressDialog.setMessage(getString(R.string.copy_message, files));
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);

        if (showMegas) {
            mProgressDialog.setMax((int) (size / MEGA_BYTE));
            mProgressDialog.setProgressNumberFormat(getString(R.string.copy_percent_megas));
        } else {
            mProgressDialog.setMax((int) size);
            mProgressDialog.setProgressNumberFormat(getString(R.string.copy_percent_bytes));
        }
        mProgressDialog.setProgress((int) progress);

        mProgressDialog.show();
    }

    private class CopyOrCutFilesTask extends AsyncTask<Void, Integer, Void> {
        private final DocumentInfo[] mDocs;
        private boolean mIsCopy;
        private boolean mFailed;

        public CopyOrCutFilesTask(DocumentInfo... docs) {
            mDocs = docs;
            mIsCopy = mClipboardIsCopy;

            long size = 0;
            for (DocumentInfo doc : docs) {
                size += doc.size;
            }

            showProgressDialog(docs.length, size, 0);
        }

        @Override
        protected Void doInBackground(Void... params) {
            final ContentResolver resolver = getContentResolver();
            ContentProviderClient client = null;

            boolean showMegas = mState.copySize > 5 * MEGA_BYTE;
            Uri currentUri = getCurrentDirectory().derivedUri;
            mFailed = false;

            for (DocumentInfo doc : mDocs) {
                try {
                    Uri cwd = currentUri;
                    if (doc.parent != null) {
                        cwd = doc.parent.copiedUri;
                    }
                    client = DocumentsApplication.acquireUnstableProviderOrThrow(
                        resolver, cwd.getAuthority());

                    // Create a new file of the same MIME type as the original
                    final Uri childUri = DocumentsContract.createDocument(
                            client, cwd, doc.mimeType, doc.displayName);

                    ContentProviderClient.releaseQuietly(client);

                    if (childUri == null) {
                        Log.e(TAG, "Failed to create a new document (uri is null)");
                        continue;
                    }

                    if (!doc.isDirectory()) {
                        final DocumentInfo copy = DocumentInfo.fromUri(resolver, childUri);

                        // Push data to the new file
                        OutputStream os = getContentResolver().openOutputStream(copy.derivedUri);
                        InputStream is = getContentResolver().openInputStream(doc.derivedUri);

                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                            float pr = len;
                            if (showMegas) {
                                pr = ((float) len) / ((float) MEGA_BYTE);
                            }
                            float count = mState.copyProgress + pr;
                            mState.copyProgress = count;
                            publishProgress((int) count);
                        }
                    } else {
                        doc.copiedUri = childUri;
                    }

                } catch (Exception e) {
                    mFailed = true;
                    Log.w(TAG, "Failed to copy " + doc, e);
                }
            }

            // If we cut, delete the original file
            if (!mFailed && !mIsCopy) {
                // We only need to delete the top level documents
                for (DocumentInfo doc : mClipboardFiles) {
                    try {
                        // get the client provider for each doc as it can be different
                        client = DocumentsApplication.acquireUnstableProviderOrThrow(
                                resolver, doc.authority);
                        DocumentsContract.deleteDocument(client, doc.derivedUri);
                    } catch (RemoteException ex) {
                    } finally {
                        ContentProviderClient.releaseQuietly(client);
                    }
                }
            }
            mClipboardFiles = null;

            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            mProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Void result) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
            mState.copyFiles = 0;
            mState.copySize = 0;
            mState.copyProgress = 0;

             // Notify that files were copied
            final Resources r = getResources();
            Toast.makeText(DocumentsActivity.this,
                r.getQuantityString(mFailed ? R.plurals.files_pasted_failed :
                    R.plurals.files_pasted, mDocs.length, mDocs.length),
                Toast.LENGTH_SHORT).show();

            // Update the action bar buttons
            invalidateOptionsMenu();

            try {
                // Hack to refresh the contents.
                DirectoryFragment.get(getFragmentManager()).onUserSortOrderChanged();
            } catch (NullPointerException ex) {
                // will fail after rotation
            }
        }
    }

    private interface DirectoryLoaderListener {

        public void onDirectoryLoaderFinished(DirectoryLoaderCallback callback,
                ArrayList<DocumentInfo> docs);
    }

    private class DirectoryLoaderCallback implements LoaderCallbacks<DirectoryResult> {

        private DirectoryLoaderListener mListener;
        private DocumentInfo mFolder;
        private int mId;

        private DirectoryLoaderCallback(int id, DocumentInfo folder,
                DirectoryLoaderListener listener) {
            mId = id;
            mListener = listener;
            mFolder = folder;
        }

        protected int getId() {
            return mId;
        }

        @Override
        public Loader<DirectoryResult> onCreateLoader(int id, Bundle args) {
            Uri contentsUri = DocumentsContract.buildChildDocumentsUri(
                    mFolder.authority, mFolder.documentId);
            return new DirectoryLoader(
                    DocumentsActivity.this, DirectoryFragment.TYPE_NORMAL,
                    getCurrentRoot(), mFolder, contentsUri, State.SORT_ORDER_DISPLAY_NAME);
        }

        @Override
        public void onLoadFinished(Loader<DirectoryResult> loader,
                DirectoryResult result) {
            ArrayList<DocumentInfo> docs = new ArrayList<DocumentInfo>();
            Cursor cursor = result == null ? null : result.cursor;
            if (cursor != null) {
                int count = cursor.getCount();
                for (int i = 0; i < count; i++) {
                    cursor.moveToPosition(i);
                    DocumentInfo doc = DocumentInfo.fromDirectoryCursor(cursor);
                    docs.add(doc);
                }
            }
            mListener.onDirectoryLoaderFinished(this, docs);
        }

        @Override
        public void onLoaderReset(Loader<DirectoryResult> loader) {
            mListener.onDirectoryLoaderFinished(this, new ArrayList<DocumentInfo>());
        }
    }

    private class ExistingFinishTask extends AsyncTask<Void, Void, Void> {
        private final Uri[] mUris;

        public ExistingFinishTask(Uri... uris) {
            mUris = uris;
        }

        @Override
        protected Void doInBackground(Void... params) {
            saveStackBlocking();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            onFinished(mUris);
        }
    }

    private class PickFinishTask extends AsyncTask<Void, Void, Void> {
        private final Uri mUri;

        public PickFinishTask(Uri uri) {
            mUri = uri;
        }

        @Override
        protected Void doInBackground(Void... params) {
            saveStackBlocking();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            onFinished(mUri);
        }
    }

    public static class State implements android.os.Parcelable {
        public int action;
        public String[] acceptMimes;

        /** Explicit user choice */
        public int userMode = MODE_UNKNOWN;
        /** Derived after loader */
        public int derivedMode = MODE_LIST;

        /** Explicit user choice */
        public int userSortOrder = SORT_ORDER_UNKNOWN;
        /** Derived after loader */
        public int derivedSortOrder = SORT_ORDER_DISPLAY_NAME;

        public boolean allowMultiple = false;
        public boolean showSize = false;
        public boolean localOnly = false;
        public boolean forceAdvanced = false;
        public boolean showAdvanced = false;
        public boolean stackTouched = false;
        public boolean restored = false;

        /** Current user navigation stack; empty implies recents. */
        public DocumentStack stack = new DocumentStack();
        /** Currently active search, overriding any stack. */
        public String currentSearch;

        /** Instance state for every shown directory */
        public HashMap<String, SparseArray<Parcelable>> dirState = Maps.newHashMap();

        public int copyFiles = 0;
        public long copySize = 0;
        public float copyProgress = 0;

        public static final int ACTION_OPEN = 1;
        public static final int ACTION_CREATE = 2;
        public static final int ACTION_GET_CONTENT = 3;
        public static final int ACTION_OPEN_TREE = 4;
        public static final int ACTION_MANAGE = 5;
        public static final int ACTION_STANDALONE = 6;

        public static final int MODE_UNKNOWN = 0;
        public static final int MODE_LIST = 1;
        public static final int MODE_GRID = 2;

        public static final int SORT_ORDER_UNKNOWN = 0;
        public static final int SORT_ORDER_DISPLAY_NAME = 1;
        public static final int SORT_ORDER_LAST_MODIFIED = 2;
        public static final int SORT_ORDER_SIZE = 3;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(action);
            out.writeInt(userMode);
            out.writeStringArray(acceptMimes);
            out.writeInt(userSortOrder);
            out.writeInt(allowMultiple ? 1 : 0);
            out.writeInt(showSize ? 1 : 0);
            out.writeInt(localOnly ? 1 : 0);
            out.writeInt(forceAdvanced ? 1 : 0);
            out.writeInt(showAdvanced ? 1 : 0);
            out.writeInt(stackTouched ? 1 : 0);
            out.writeInt(restored ? 1 : 0);
            DurableUtils.writeToParcel(out, stack);
            out.writeString(currentSearch);
            out.writeMap(dirState);
            out.writeInt(copyFiles);
            out.writeLong(copySize);
            out.writeFloat(copyProgress);
        }

        public static final Creator<State> CREATOR = new Creator<State>() {
            @Override
            public State createFromParcel(Parcel in) {
                final State state = new State();
                state.action = in.readInt();
                state.userMode = in.readInt();
                state.acceptMimes = in.readStringArray();
                state.userSortOrder = in.readInt();
                state.allowMultiple = in.readInt() != 0;
                state.showSize = in.readInt() != 0;
                state.localOnly = in.readInt() != 0;
                state.forceAdvanced = in.readInt() != 0;
                state.showAdvanced = in.readInt() != 0;
                state.stackTouched = in.readInt() != 0;
                state.restored = in.readInt() != 0;
                DurableUtils.readFromParcel(in, state.stack);
                state.currentSearch = in.readString();
                in.readMap(state.dirState, null);
                state.copyFiles = in.readInt();
                state.copySize = in.readLong();
                state.copyProgress = in.readFloat();
                return state;
            }

            @Override
            public State[] newArray(int size) {
                return new State[size];
            }
        };
    }

    private void dumpStack() {
        Log.d(TAG, "Current stack: ");
        Log.d(TAG, " * " + mState.stack.root);
        for (DocumentInfo doc : mState.stack) {
            Log.d(TAG, " +-- " + doc);
        }
    }

    public static DocumentsActivity get(Fragment fragment) {
        return (DocumentsActivity) fragment.getActivity();
    }
}
