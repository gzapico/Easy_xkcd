package de.tap.easy_xkcd.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.misc.HackyViewPager;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.PrefHelper;
import uk.co.senab.photoview.PhotoView;

/**
 * Superclass for ComicBrowserFragment, OfflineFragment & FavoritesFragment
 */

public class ComicFragment extends android.support.v4.app.Fragment {
    public int lastComicNumber;
    public int newestComicNumber;
    public SparseArray<Comic> comicMap = new SparseArray<>();

    public HackyViewPager mPager;
    public static boolean fromSearch = false;

    protected PrefHelper prefHelper;

    protected View inflateLayout(int resId, LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(resId, container, false);
        setHasOptionsMenu(true);

        mPager = (HackyViewPager) view.findViewById(R.id.pager);
        mPager.setOffscreenPageLimit(3);

        prefHelper = ((MainActivity) getActivity()).getPrefHelper();

        if (savedInstanceState != null) {
            lastComicNumber = savedInstanceState.getInt("Last Comic");
        } else if (lastComicNumber == 0) {
            lastComicNumber = prefHelper.getLastComic();
        }

        if (((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser && prefHelper.subtitleEnabled())
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(lastComicNumber));

        //TODO Extend ComicBrowserPagerAdapter and OfflineBrowserPagerAdapter from a single class, extend FavortesFragment from ComicFragment

        return view;
    }

    protected class SaveComicImageTask extends AsyncTask<Boolean, Void, Void> {
        protected int mAddedNumber = lastComicNumber;
        private Bitmap mBitmap;
        private Comic mAddedComic;
        private boolean downloadImage;

        @Override
        protected Void doInBackground(Boolean... downloadImage) {
            this.downloadImage = downloadImage[0];
            if (this.downloadImage) {
               mAddedComic = comicMap.get(lastComicNumber);
                try {
                    String url = mAddedComic.getComicData()[2];
                    mBitmap = Glide
                            .with(getActivity())
                            .load(url)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .into(-1, -1)
                            .get();
                } catch (Exception e) {
                    Favorites.removeFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
                    Log.e("Saving Image failed!", e.toString());
                }
                prefHelper.addTitle(mAddedComic.getComicData()[0], mAddedNumber);
                prefHelper.addAlt(mAddedComic.getComicData()[1], mAddedNumber);
            }

            Favorites.addFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (downloadImage) {
                try {
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    dir.mkdirs();
                    File file = new File(dir, String.valueOf(mAddedNumber) + ".png");
                    FileOutputStream fos = new FileOutputStream(file);
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    Log.e("Error", "Saving to external storage failed");
                    try {
                        FileOutputStream fos = getActivity().openFileOutput(String.valueOf(mAddedNumber), Context.MODE_PRIVATE);
                        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
            //refresh the FavoritesFragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null)
                f.refresh();
            //Sometimes the floating action button does not animate back to the bottom when the snackbar is dismissed, so force it to its original position
            ((MainActivity) getActivity()).getFab().forceLayout();
            getActivity().invalidateOptionsMenu();
        }
    }

    protected class DeleteComicImageTask extends AsyncTask<Boolean, Void, Void> {
        protected int mRemovedNumber = lastComicNumber;
        protected View.OnClickListener oc;

        @Override
        protected Void doInBackground(final Boolean... deleteImage) {
            if (deleteImage[0]) {
                //delete the image from internal storage
                getActivity().deleteFile(String.valueOf(mRemovedNumber));
                //delete from external storage
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File file = new File(dir, String.valueOf(mRemovedNumber) + ".png");
                file.delete();

                prefHelper.addTitle("", mRemovedNumber);
                prefHelper.addAlt("", mRemovedNumber);
            }
            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new SaveComicImageTask().execute(deleteImage[0]);
                }
            };
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));
            Snackbar.make(((MainActivity) getActivity()).getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_undo, oc)
                    .show();
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            //refresh the favorites fragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null)
                f.refresh();
        }
    }

    public boolean zoomReset() {
        PhotoView pv = (PhotoView) mPager.findViewWithTag(lastComicNumber - 1).findViewById(R.id.ivComic);
        float scale = pv.getScale();
        if (scale != 1f) {
            pv.setScale(1f, true);
            return true;
        } else {
            return false;
        }
    }

    protected boolean explainComic(int number) {
        String url = "http://explainxkcd.com/" + String.valueOf(number);
        CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        intentBuilder.setToolbarColor(typedValue.data);
        CustomTabActivityHelper.openCustomTab(getActivity(), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
        return true;
    }

    protected boolean openComicInBrowser(int number) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://xkcd.com/" + String.valueOf(number)));
        startActivity(intent);
        return true;
    }

    protected boolean showTranscript(String trans) {
        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
        mDialog.setMessage(trans);
        mDialog.show();
        return true;
    }

    protected boolean addBookmark(int bookmark) {
        if (prefHelper.getBookmark() == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast, Toast.LENGTH_LONG).show();
        prefHelper.setBookmark(bookmark);
        OverviewListFragment.bookmark = bookmark;
        return true;
    }

    protected void shareComicUrl() {
        //shares the comics url along with its title
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, comicMap.get(lastComicNumber).getComicData()[0]);
        if (prefHelper.shareMobile()) {
            share.putExtra(Intent.EXTRA_TEXT, "http://m.xkcd.com/" + String.valueOf(lastComicNumber));
        } else {
            share.putExtra(Intent.EXTRA_TEXT, "http://xkcd.com/" + String.valueOf(lastComicNumber));
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    protected void shareComicImage(Uri uri) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, comicMap.get(lastComicNumber).getComicData()[0]);
        if (prefHelper.shareAlt()) {
            share.putExtra(Intent.EXTRA_TEXT, comicMap.get(lastComicNumber).getComicData()[1]);
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    protected boolean getRandomComic() {
        lastComicNumber = prefHelper.getRandomNumber(lastComicNumber);
        mPager.setCurrentItem(lastComicNumber - 1, false);
        return true;
    }

    protected void getPreviousRandom() {
        lastComicNumber = prefHelper.getPreviousRandom(lastComicNumber);
        mPager.setCurrentItem(lastComicNumber - 1, false);
    }

    protected boolean getLatestComic() {
        lastComicNumber = newestComicNumber;
        mPager.setCurrentItem(lastComicNumber - 1, false);
        return true;
    }

    protected void scrollViewPager() {
        if (lastComicNumber != 0) {
            try {
                Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                field.setAccessible(true);
                field.set(mPager, lastComicNumber - 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean setAltText() {
        //If the user selected the menu item for the first time, show the toast
        if (prefHelper.showAltTip()) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            prefHelper.setAltTip(false);
        }
        //Show alt text
        TextView tvAlt = (TextView) mPager.findViewWithTag(lastComicNumber - 1).findViewById(R.id.tvAlt);
        if (prefHelper.classicAltStyle()) {
            toggleVisibility(tvAlt);
        } else {
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
            mDialog.setMessage(tvAlt.getText());
            mDialog.show();
        }
        return true;
    }

    protected void toggleVisibility(View view) {
        // Switches a view's visibility between GONE and VISIBLE
        if (view.getVisibility() == View.GONE) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    protected void pageSelected(int position) {
        try {
            getActivity().invalidateOptionsMenu();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        prefHelper.setComicRead(String.valueOf(position + 1));
        lastComicNumber = position + 1;
        if (prefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser)
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(lastComicNumber));
    }

    @Override
    public void onStop() {
        //TODO check instanceof FavoritesFragment
        prefHelper.setLastComic(lastComicNumber);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_alt:
                return setAltText();

            case R.id.action_explain:
                return explainComic(lastComicNumber);

            case R.id.action_latest:
                return getLatestComic();

            case R.id.action_browser:
                return openComicInBrowser(lastComicNumber);

            case R.id.action_trans:
                return showTranscript(comicMap.get(lastComicNumber).getTranscript());

            case R.id.action_boomark:
                return addBookmark(lastComicNumber);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        //Update the favorites icon
        MenuItem fav = menu.findItem(R.id.action_favorite);
        if (Favorites.checkFavorite(getActivity(), lastComicNumber)) {
            fav.setIcon(R.drawable.ic_action_favorite);
            fav.setTitle(R.string.action_favorite_remove);
        } else {
            fav.setIcon(R.drawable.ic_favorite_outline);
            fav.setTitle(R.string.action_favorite);
        }
        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("Last Comic", lastComicNumber);
        super.onSaveInstanceState(savedInstanceState);
    }

    public void scrollTo(int pos, boolean smooth) {
        mPager.setCurrentItem(pos, smooth);
    }

}