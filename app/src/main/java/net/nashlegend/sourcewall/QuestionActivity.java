package net.nashlegend.sourcewall;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;

import net.nashlegend.sourcewall.adapters.QuestionDetailAdapter;
import net.nashlegend.sourcewall.commonview.LListView;
import net.nashlegend.sourcewall.commonview.LoadingView;
import net.nashlegend.sourcewall.connection.ResultObject;
import net.nashlegend.sourcewall.connection.api.QuestionAPI;
import net.nashlegend.sourcewall.connection.api.UserAPI;
import net.nashlegend.sourcewall.dialogs.FavorDialog;
import net.nashlegend.sourcewall.dialogs.InputDialog;
import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.Question;
import net.nashlegend.sourcewall.util.AutoHideUtil;
import net.nashlegend.sourcewall.util.Consts;
import net.nashlegend.sourcewall.util.ToastUtil;
import net.nashlegend.sourcewall.view.AnswerListItemView;

import java.util.ArrayList;


public class QuestionActivity extends SwipeActivity implements LListView.OnRefreshListener, View.OnClickListener, LoadingView.ReloadListener {

    private LListView listView;
    private QuestionDetailAdapter adapter;
    private Question question;
    private LoaderTask task;
    private LoadingView loadingView;
    private String notice_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(net.nashlegend.sourcewall.R.layout.activity_question);
        loadingView = (LoadingView) findViewById(net.nashlegend.sourcewall.R.id.question_progress_loading);
        loadingView.setReloadListener(this);
        Toolbar toolbar = (Toolbar) findViewById(net.nashlegend.sourcewall.R.id.action_bar);
        setSupportActionBar(toolbar);
        question = (Question) getIntent().getSerializableExtra(Consts.Extra_Question);
        notice_id = getIntent().getStringExtra(Consts.Extra_Notice_Id);
        listView = (LListView) findViewById(net.nashlegend.sourcewall.R.id.list_detail);
        adapter = new QuestionDetailAdapter(this);
        listView.setAdapter(adapter);

        listView.setCanPullToLoadMore(false);
        listView.setOnRefreshListener(this);
        listView.setOnItemClickListener(onItemClickListener);

        FloatingActionsMenu floatingActionsMenu = (FloatingActionsMenu) findViewById(net.nashlegend.sourcewall.R.id.layout_operation);
        FloatingActionButton replyButton = (FloatingActionButton) findViewById(net.nashlegend.sourcewall.R.id.button_reply);
        FloatingActionButton recomButton = (FloatingActionButton) findViewById(net.nashlegend.sourcewall.R.id.button_recommend);
        FloatingActionButton favorButton = (FloatingActionButton) findViewById(net.nashlegend.sourcewall.R.id.button_favor);

        replyButton.setOnClickListener(this);
        recomButton.setOnClickListener(this);
        favorButton.setOnClickListener(this);

        AutoHideUtil.applyListViewAutoHide(this, listView, toolbar, floatingActionsMenu, (int) getResources().getDimension(net.nashlegend.sourcewall.R.dimen.abc_action_bar_default_height_material));

        loadData(-1);
    }

    private void loadData(int offset) {
        cancelPotentialTask();
        task = new LoaderTask();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, offset);
    }

    private void cancelPotentialTask() {
        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) {
            task.cancel(true);
            listView.doneOperation();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(net.nashlegend.sourcewall.R.menu.menu_question, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStartRefresh() {
        loadData(-1);
    }

    @Override
    public void onStartLoadMore() {
        loadData(adapter.getCount() - 1);
    }

    AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            onReplyItemClick(view, position, id);
        }
    };

    private void onReplyItemClick(final View view, int position, long id) {
        if (view instanceof AnswerListItemView) {
            Intent intent = new Intent(this, AnswerActivity.class);
            intent.putExtra(Consts.Extra_Answer, ((AnswerListItemView) view).getData());
            intent.putExtra(Consts.Extra_Question, question);
            startActivity(intent);
            overridePendingTransition(net.nashlegend.sourcewall.R.anim.slide_in_right, 0);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case net.nashlegend.sourcewall.R.id.button_reply:
                answerQuestion();
                break;
            case net.nashlegend.sourcewall.R.id.button_recommend:
                recommend();
                break;
            case net.nashlegend.sourcewall.R.id.button_favor:
                favor();
                break;
        }
    }

    private void favor() {
        if (!UserAPI.isLoggedIn()) {
            notifyNeedLog();
        } else {
            new FavorDialog.Builder(this).setTitle(net.nashlegend.sourcewall.R.string.action_favor).create(question).show();
        }
    }

    private void recommend() {
        if (!UserAPI.isLoggedIn()) {
            notifyNeedLog();
        } else {
            InputDialog.Builder builder = new InputDialog.Builder(this);
            builder.setTitle(net.nashlegend.sourcewall.R.string.recommend_article);
            builder.setCancelable(true);
            builder.setCanceledOnTouchOutside(false);
            builder.setOnClickListener(new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        InputDialog d = (InputDialog) dialog;
                        String text = d.InputString;
                        RecommendTask recommendTask = new RecommendTask();
                        recommendTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, question.getId(), question.getTitle(), question.getSummary(), text);
                    } else {
                        // cancel recommend
                    }
                }
            });
            InputDialog inputDialog = builder.create();
            inputDialog.show();
        }
    }

    private void answerQuestion() {
        if (!UserAPI.isLoggedIn()) {
            notifyNeedLog();
        } else {
            Intent intent = new Intent(this, ReplyActivity.class);
            intent.putExtra(Consts.Extra_Ace_Model, question);
            startActivity(intent);
            overridePendingTransition(net.nashlegend.sourcewall.R.anim.slide_in_right, 0);
        }
    }

    @Override
    public void reload() {
        loadData(-1);
    }

    class LoaderTask extends AsyncTask<Integer, Integer, ResultObject> {
        int offset;

        @Override
        protected ResultObject doInBackground(Integer... params) {
            if (!TextUtils.isEmpty(notice_id)) {
                UserAPI.ignoreOneNotice(notice_id);
                notice_id = null;
            }
            offset = params[0];
            if (offset < 0) {
                return QuestionAPI.getQuestionFirstPage(question);
            } else {
                return QuestionAPI.getQuestionAnswers(question.getId(), offset);
            }
        }

        @Override
        protected void onPostExecute(ResultObject result) {
            if (result.ok) {
                loadingView.onLoadSuccess();
                ArrayList<AceModel> ars = (ArrayList<AceModel>) result.result;
                if (offset < 0) {
                    //Refresh
                    if (ars.size() > 0) {
                        adapter.setList(ars);
                        adapter.notifyDataSetInvalidated();
                    }
                } else {
                    //Load More
                    if (ars.size() > 0) {
                        adapter.addAll(ars);
                        adapter.notifyDataSetChanged();
                    }
                }
                if (adapter.getCount() > 0) {
                    listView.setCanPullToLoadMore(true);
                } else {
                    listView.setCanPullToLoadMore(false);
                }
            } else {
                if (result.statusCode == 404) {
                    ToastUtil.toastSingleton(R.string.page_404);
                    finish();
                } else {
                    ToastUtil.toastSingleton(getString(net.nashlegend.sourcewall.R.string.load_failed));
                    loadingView.onLoadFailed();
                }
            }
            listView.doneOperation();
        }
    }

    class RecommendTask extends AsyncTask<String, Integer, ResultObject> {

        @Override
        protected ResultObject doInBackground(String... params) {
            String questionID = params[0];
            String title = params[1];
            String summary = params[2];
            String comment = params[3];
            return QuestionAPI.recommendQuestion(questionID, title, summary, comment);
        }

        @Override
        protected void onPostExecute(ResultObject resultObject) {
            if (resultObject.ok) {
                ToastUtil.toast(net.nashlegend.sourcewall.R.string.recommend_ok);
            } else {
                ToastUtil.toast(net.nashlegend.sourcewall.R.string.recommend_failed);
            }
        }
    }
}
