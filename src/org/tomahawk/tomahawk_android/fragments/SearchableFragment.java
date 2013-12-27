/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2013, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.fragments;

import org.tomahawk.libtomahawk.collection.Album;
import org.tomahawk.libtomahawk.collection.Artist;
import org.tomahawk.libtomahawk.collection.UserPlaylist;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.TomahawkApp;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.adapters.TomahawkBaseAdapter;
import org.tomahawk.tomahawk_android.adapters.TomahawkListAdapter;
import org.tomahawk.tomahawk_android.services.PlaybackService;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TomahawkFragment} which offers both local and non-local search functionality to the user.
 */
public class SearchableFragment extends TomahawkFragment
        implements OnItemClickListener, CompoundButton.OnCheckedChangeListener,
        TextView.OnEditorActionListener {

    public static final String SEARCHABLEFRAGMENT_QUERY_STRING
            = "org.tomahawk.tomahawk_android.SEARCHABLEFRAGMENT_QUERY_ID";

    private SearchableFragment mSearchableFragment = this;

    private ArrayList<Query> mCurrentShownQueries;

    private ArrayList<Album> mCurrentShownAlbums;

    private ArrayList<Artist> mCurrentShownArtists;

    private String mCurrentQueryString;

    private EditText mSearchEditText = null;

    /**
     * Restore the {@link String} inside the search {@link TextView}. Either through the
     * savedInstanceState {@link Bundle} or through the a {@link Bundle} provided in the Arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState
                .containsKey(SEARCHABLEFRAGMENT_QUERY_STRING)
                && savedInstanceState.getString(SEARCHABLEFRAGMENT_QUERY_STRING) != null) {
            mCurrentQueryString = savedInstanceState.getString(SEARCHABLEFRAGMENT_QUERY_STRING);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getArguments() != null && getArguments().containsKey(SEARCHABLEFRAGMENT_QUERY_STRING)
                && getArguments().getString(SEARCHABLEFRAGMENT_QUERY_STRING) != null) {
            mCurrentQueryString = getArguments().getString(SEARCHABLEFRAGMENT_QUERY_STRING);
        }

        setSearchText((EditText) mTomahawkMainActivity.getSupportActionBar().getCustomView()
                .findViewById(R.id.search_edittext));
        AutoCompleteTextView textView = (AutoCompleteTextView) mTomahawkMainActivity
                .getSupportActionBar().getCustomView().findViewById(R.id.search_edittext);
        // Sets the background colour to grey so that the text is visible
        textView.setDropDownBackgroundResource(R.drawable.menu_dropdown_panel_tomahawk);
        setupAutoComplete();

        // Initialize our onlineSourcesCheckBox
        CheckBox onlineSourcesCheckBox = (CheckBox) mTomahawkMainActivity
                .findViewById(R.id.search_onlinesources_checkbox);
        onlineSourcesCheckBox.setOnCheckedChangeListener(this);

        // If we have restored a CurrentQueryString, start searching, so that we show the proper
        // results again
        if (mCurrentQueryString != null) {
            resolveFullTextQuery(mCurrentQueryString);
            mSearchEditText.setText(mCurrentQueryString);
            mSearchEditText.setSelection(mCurrentQueryString.length());
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Always hide the soft keyboard, if we pause this Fragment
        InputMethodManager imm = (InputMethodManager) mTomahawkMainActivity
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
    }

    /**
     * Save the {@link String} inside the search {@link TextView}.
     */
    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putString(SEARCHABLEFRAGMENT_QUERY_STRING, mCurrentQueryString);
        super.onSaveInstanceState(out);
    }

    /**
     * Called every time an item inside the {@link org.tomahawk.tomahawk_android.views.TomahawkStickyListHeadersListView}
     * is clicked
     *
     * @param parent   The AdapterView where the click happened.
     * @param view     The view within the AdapterView that was clicked (this will be a view
     *                 provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id       The row id of the item that was clicked.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        position -= getListView().getHeaderViewsCount();
        if (position >= 0) {
            Object item = getListAdapter().getItem(position);
            if (item instanceof Query) {
                UserPlaylist playlist = UserPlaylist
                        .fromQueryList(TomahawkApp.getLifetimeUniqueStringId(), mCurrentQueryString,
                                mCurrentShownQueries, mCurrentShownQueries.get(position));
                PlaybackService playbackService = mTomahawkMainActivity.getPlaybackService();
                if (playbackService != null) {
                    playbackService.setCurrentPlaylist(playlist);
                    playbackService.start();
                }
                mTomahawkMainActivity.getContentViewer()
                        .setCurrentHubId(TomahawkMainActivity.HUB_ID_PLAYBACK);
            } else if (item instanceof Album) {
                Bundle bundle = new Bundle();
                String key = TomahawkUtils.getCacheKey((Album) item);
                bundle.putString(TOMAHAWK_ALBUM_KEY, key);
                mTomahawkMainActivity.getContentViewer().replace(mCorrespondingHubId,
                        TracksFragment.class, key, TOMAHAWK_ALBUM_KEY, false, false);
            } else if (item instanceof Artist) {
                Bundle bundle = new Bundle();
                String key = TomahawkUtils.getCacheKey((Artist) item);
                bundle.putString(TOMAHAWK_ARTIST_KEY, key);
                mTomahawkMainActivity.getContentViewer().replace(mCorrespondingHubId,
                        AlbumsFragment.class, key, TOMAHAWK_ARTIST_KEY, false, false);
            }
        }
    }

    /**
     * Called, when the checkbox' state has been changed,
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        resolveFullTextQuery(mCurrentQueryString);
    }

    /**
     * Used to determine, if the user has pressed the confirmation button on his soft keyboard.
     * (The
     * button in the bottom right corner of the soft keyboard on most smartphones/tablets)
     */
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (event == null || actionId == EditorInfo.IME_ACTION_SEARCH
                || actionId == EditorInfo.IME_ACTION_DONE
                || event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            InputMethodManager imm = (InputMethodManager) mTomahawkMainActivity
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
            String searchText = textView.getText().toString();
            if (searchText != null && !TextUtils.isEmpty(searchText)) {
                addToAutoCompleteArray(searchText);
                setupAutoComplete();
                resolveFullTextQuery(searchText);
                return true;
            }
        }
        return false;
    }

    /**
     * Display all {@link org.tomahawk.libtomahawk.resolver.Result}s of the {@link Query} with the
     * given id
     */
    public void showQueryResults(String qid) {
        Query query = mPipeline.getQuery(qid);
        mCurrentQueryString = query.getFullTextQuery();
        List<List<TomahawkBaseAdapter.TomahawkListItem>> listArray
                = new ArrayList<List<TomahawkBaseAdapter.TomahawkListItem>>();
        ArrayList<TomahawkBaseAdapter.TomahawkListItem> trackResultList
                = new ArrayList<TomahawkBaseAdapter.TomahawkListItem>();
        mCurrentShownQueries = query.getTrackQueries();
        trackResultList.addAll(mCurrentShownQueries);
        listArray.add(trackResultList);
        ArrayList<TomahawkBaseAdapter.TomahawkListItem> artistResultList
                = new ArrayList<TomahawkBaseAdapter.TomahawkListItem>();
        mCurrentShownArtists = query.getArtists();
        artistResultList.addAll(mCurrentShownArtists);
        listArray.add(artistResultList);
        ArrayList<TomahawkBaseAdapter.TomahawkListItem> albumResultList
                = new ArrayList<TomahawkBaseAdapter.TomahawkListItem>();
        mCurrentShownAlbums = query.getAlbums();
        albumResultList.addAll(mCurrentShownAlbums);
        listArray.add(albumResultList);
        if (getListAdapter() == null) {
            TomahawkListAdapter tomahawkListAdapter = new TomahawkListAdapter(mTomahawkMainActivity,
                    listArray);
            tomahawkListAdapter.setShowCategoryHeaders(true);
            tomahawkListAdapter.setShowResolvedBy(true);
            setListAdapter(tomahawkListAdapter);
        } else {
            ((TomahawkListAdapter) getListAdapter()).setListArray(listArray);
        }
        getListView().setOnItemClickListener(mSearchableFragment);
    }

    /**
     * Setup this {@link SearchableFragment}s {@link AutoCompleteTextView}
     */
    private void setupAutoComplete() {
        AutoCompleteTextView textView = (AutoCompleteTextView) mTomahawkMainActivity
                .getSupportActionBar().getCustomView().findViewById(R.id.search_edittext);
        ArrayList<String> autoCompleteSuggestions = getAutoCompleteArray();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mTomahawkMainActivity,
                android.R.layout.simple_list_item_1, autoCompleteSuggestions);
        textView.setAdapter(adapter);
    }

    /**
     * Set the reference to the searchText, that is used to filter the custom {@link
     * android.widget.ListView}
     *
     * @param searchText the EditText object which the listener is connected to
     */
    public void setSearchText(EditText searchText) {
        mSearchEditText = searchText;
        if (mSearchEditText != null) {
            mSearchEditText.setOnEditorActionListener(this);
            mSearchEditText.setImeActionLabel("Go", KeyEvent.KEYCODE_ENTER);
            mSearchEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        }
    }

    /**
     * Invoke the resolving process with the given fullTextQuery {@link String}
     */
    public void resolveFullTextQuery(String fullTextQuery) {
        mTomahawkMainActivity.getContentViewer().backToRoot(mCorrespondingHubId, false);
        mCurrentQueryString = fullTextQuery;
        CheckBox onlineSourcesCheckBox = (CheckBox) mTomahawkMainActivity
                .findViewById(R.id.search_onlinesources_checkbox);
        String queryId = mPipeline.resolve(fullTextQuery, !onlineSourcesCheckBox.isChecked());
        mCorrespondingQueryIds.clear();
        if (queryId != null) {
            mCorrespondingQueryIds.add(queryId);
            mTomahawkMainActivity.startLoadingAnimation();
        }
    }

    /**
     * Add the given {@link String} to the {@link ArrayList}, which is being persisted as a {@link
     * SharedPreferences}
     */
    public void addToAutoCompleteArray(String newString) {
        ArrayList<String> myArrayList = getAutoCompleteArray();
        int highestIndex = myArrayList.size();

        for (String aMyArrayList : myArrayList) {
            if (newString != null && newString.equals(aMyArrayList)) {
                return;
            }
        }

        myArrayList.add(newString);

        SharedPreferences sPrefs = PreferenceManager
                .getDefaultSharedPreferences(mTomahawkMainActivity.getBaseContext());
        SharedPreferences.Editor sEdit = sPrefs.edit();

        sEdit.putString("autocomplete_" + highestIndex, myArrayList.get(highestIndex));
        sEdit.putInt("autocomplete_size", myArrayList.size());
        sEdit.commit();
    }

    /**
     * @return the {@link ArrayList} of {@link String}s containing every {@link String} in our
     * autocomplete array
     */
    public ArrayList<String> getAutoCompleteArray() {
        SharedPreferences sPrefs = PreferenceManager
                .getDefaultSharedPreferences(mTomahawkMainActivity.getBaseContext());
        ArrayList<String> myAList = new ArrayList<String>();
        int size = sPrefs.getInt("autocomplete_size", 0);

        for (int j = 0; j < size; j++) {
            myAList.add(sPrefs.getString("autocomplete_" + j, null));
        }
        return myAList;
    }

    @Override
    protected void onPipeLineResultsReported(String qId) {
        if (mCorrespondingQueryIds.contains(qId)) {
            mTomahawkMainActivity.getContentViewer()
                    .getBackStackAtPosition(mCorrespondingHubId)
                    .get(0).queryString = mCurrentQueryString;
            showQueryResults(qId);
        }
    }
}
