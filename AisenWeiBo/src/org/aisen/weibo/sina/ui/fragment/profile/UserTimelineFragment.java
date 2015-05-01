package org.aisen.weibo.sina.ui.fragment.profile;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;

import com.m.component.container.FragmentArgs;
import com.m.component.container.FragmentContainerActivity;
import com.m.network.http.Params;
import com.m.network.task.TaskException;
import com.m.ui.activity.basic.BaseActivity;
import com.m.ui.fragment.AStripTabsFragment;

import org.aisen.weibo.sina.R;
import org.aisen.weibo.sina.base.AppContext;
import org.aisen.weibo.sina.base.AppSettings;
import org.aisen.weibo.sina.ui.fragment.timeline.ATimelineFragment;
import org.sina.android.SinaSDK;
import org.sina.android.bean.AccessToken;
import org.sina.android.bean.StatusContent;
import org.sina.android.bean.StatusContents;
import org.sina.android.bean.Token;
import org.sina.android.bean.WeiBoUser;

/**
 * 用户的微博
 *
 * Created by wangdan on 15-3-1.
 */
public class UserTimelineFragment extends ATimelineFragment
                                       implements UserProfilePagerFragment.IUserProfileRefresh,
                                                  AStripTabsFragment.IStripTabInitData {

    public static UserTimelineFragment newInstance(WeiBoUser user, String feature) {
        Bundle args = new Bundle();
        args.putSerializable("user", user);
        args.putSerializable("launch", false);
        if (!TextUtils.isEmpty(feature))
            args.putSerializable("feature", feature);

        UserTimelineFragment fragment = new UserTimelineFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static void launch(Activity from, WeiBoUser user) {
        FragmentArgs args = new FragmentArgs();
        args.add("user", user);
        args.add("launch", true);

        FragmentContainerActivity.launch(from, UserTimelineFragment.class, args);
    }

    private boolean launch;
    private WeiBoUser mUser;
    private String feature;

    @Override
    protected int inflateContentView() {
        return R.layout.as_ui_user_timeline;
    }

    @Override
    protected void layoutInit(LayoutInflater inflater, Bundle savedInstanceSate) {
        super.layoutInit(inflater, savedInstanceSate);

        mUser = savedInstanceSate == null ? (WeiBoUser) getArguments().getSerializable("user")
                                          : (WeiBoUser) savedInstanceSate.getSerializable("user");
        launch = savedInstanceSate == null ? getArguments().getBoolean("launch", false)
                                           : savedInstanceSate.getBoolean("launch");
        feature = savedInstanceSate == null ? getArguments().getString("feature", null)
                                            : savedInstanceSate.getString("feature", null);

        if (launch) {
            BaseActivity activity = (BaseActivity) getActivity();
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setTitle(mUser.getScreen_name());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (!TextUtils.isEmpty(feature))
            outState.putString("feature", feature);
        outState.putBoolean("launch", launch);
        outState.putSerializable("user", mUser);
    }

    @Override
    public void onStripTabRequestData() {
        // 如果还没有加载过数据，就开始加载
        if (getTaskCount("TimelineTask") == 0) {
            requestData(RefreshMode.reset);
        }
    }

    @Override
    protected void requestData(RefreshMode mode) {
        boolean load = true;

        // 如果还没有加载过数据，切且显示的是当前的页面
        if (getTaskCount("TimelineTask") == 0) {
            Fragment fragment = getPagerCurrentFragment();
            if (fragment != null && fragment != this)
                load = false;
        }

        if (load)
            new UserTimelineTask(mode == RefreshMode.refresh ? RefreshMode.reset : mode).execute();
    }

    @Override
    public void refreshProfile() {
        if (!isRefreshing())
            requestDataDelay(100);
    }

    // 用户微博列表
    class UserTimelineTask extends TimelineTask {

        public UserTimelineTask(RefreshMode mode) {
            super(mode);
        }

        @Override
        protected StatusContents workInBackground(RefreshMode mode, String previousPage, String nextPage,
                                                  Void... p) throws TaskException {
            Params params = new Params();

            if (mode == RefreshMode.refresh && !TextUtils.isEmpty(previousPage))
                params.addParameter("since_id", previousPage);

            if (mode == RefreshMode.update && !TextUtils.isEmpty(nextPage))
                params.addParameter("max_id", nextPage);

            // 是否是原创
            if (!TextUtils.isEmpty(feature))
			    params.addParameter("feature", feature);

            // 不管user_id字段传值什么，都返回登录用户的微博
            if (AppContext.getUser().getIdstr().equals(mUser.getIdstr())) {
                params.addParameter("user_id", mUser.getIdstr());
            }
            else {
                params.addParameter("screen_name", mUser.getScreen_name());
            }

            params.addParameter("count", String.valueOf(AppSettings.getTimelineCount()));

            Token token = null;
            // 是当前登录用户
            if (params.containsKey("user_id") && params.getParameter("user_id").equals(AppContext.getUser().getIdstr())) {
            }
            else if (params.containsKey("screen_name") && params.getParameter("screen_name").equals(AppContext.getUser().getScreen_name())) {
            }
            else {
                if (AppContext.getAdvancedToken() != null) {
                    AccessToken accessToken = AppContext.getAdvancedToken();

                    token = new Token();
                    token.setToken(accessToken.getToken());
                    token.setSecret(accessToken.getSecret());

                    params.addParameter("source", accessToken.getAppKey());
                }
            }
            if (token == null)
                token = AppContext.getToken();
            StatusContents statusContents = SinaSDK.getInstance(token, getTaskCacheMode(this)).statusesUserTimeLine(params);

            if (statusContents != null && statusContents.getStatuses() != null && statusContents.getStatuses().size() > 0) {
                for (StatusContent status : statusContents.getStatuses())
                    status.setUser(mUser);
            }

            return statusContents;
        }

        @Override
        protected void onSuccess(StatusContents result) {
            super.onSuccess(result);

            if (result == null)
                return;

            getActivity().invalidateOptionsMenu();
        }

    }

}
