package com.wj.jd.widget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.Gson;
import com.wj.jd.MyApplication;
import com.wj.jd.R;
import com.wj.jd.util.StringCallBack;
import com.wj.jd.bean.JingDouBean;
import com.wj.jd.bean.RedPacket;
import com.wj.jd.bean.UserBean;
import com.wj.jd.util.CacheUtil;
import com.wj.jd.util.HttpUtil;
import com.wj.jd.util.BitmapUtil;
import com.wj.jd.util.TimeUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class UpdateDataService extends Service {
    Timer timer;

    Gson gson = new Gson();

    RemoteViews remoteViews;

    int page = 1;
    long todayTime, yesterdayTime;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    updata();
                }
            }, 0, 10 * 60 * 1000);
        } else {
            updata();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void updata() {
        //更新的逻辑
        remoteViews = new RemoteViews(getPackageName(), R.layout.widges_layout);
        AppWidgetManager manager = AppWidgetManager.getInstance(getApplicationContext());
        ComponentName componentName = new ComponentName(getApplicationContext(), MyAppWidgetProvider.class);
        manager.updateAppWidget(componentName, remoteViews);

        getUserInfo();
        getUserInfo1();
        page = 1;
        UserBean.INSTANCE.setTodayBean(0);
        UserBean.INSTANCE.setAgo1Bean(0);
        todayTime = TimeUtil.INSTANCE.getTodayMillis(0);
        yesterdayTime = TimeUtil.INSTANCE.getTodayMillis(-1);
        getJingBeanData();

        getRedPackge();
    }

    private void getRedPackge() {
        HttpUtil.INSTANCE.getRedPack("https://m.jingxi.com/user/info/QueryUserRedEnvelopesV2?type=1&orgFlag=JD_PinGou_New&page=1&cashRedType=1&redBalanceFlag=1&channel=1&_=" + System.currentTimeMillis() + "&sceneval=2&g_login_type=1&g_ty=ls", new StringCallBack() {
            @Override
            public void onSuccess(@NonNull String result) {
                try {
                    RedPacket redPacket = gson.fromJson(result, RedPacket.class);
                    UserBean.INSTANCE.setHb(redPacket.getData().getBalance());
                    UserBean.INSTANCE.setGqhb(redPacket.getData().getExpiredBalance());

                    setData();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onFail() {

            }
        });
    }

    private void getJingBeanData() {
        HttpUtil.INSTANCE.getJD("https://api.m.jd.com/client.action?functionId=getJingBeanBalanceDetail", page, new StringCallBack() {
            @Override
            public void onSuccess(@NonNull String result) {
                try {
                    //2021-10-05 10:18:37
                    JingDouBean jingDouBean = gson.fromJson(result, JingDouBean.class);
                    ArrayList<JingDouBean.DetailListDTO> dataList = jingDouBean.getDetailList();
                    boolean isFinish = true;
                    for (int i = 0; i < dataList.size(); i++) {
                        JingDouBean.DetailListDTO detail = dataList.get(i);
                        long beanDay = TimeUtil.INSTANCE.parseTime(detail.getDate());
                        if (beanDay > todayTime) {
                            if (detail.getAmount() > 0) {
                                UserBean.INSTANCE.setTodayBean(UserBean.INSTANCE.getTodayBean() + detail.getAmount());
                            }
                        } else {
                            isFinish = false;
                            break;
                        }
                    }
                    if (isFinish) {
                        page++;
                        getJingBeanData();
                    } else {
                        get1AgoBeanData();
                        setData();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFail() {

            }
        });

    }

    private void get1AgoBeanData() {
        HttpUtil.INSTANCE.getJD("https://api.m.jd.com/client.action?functionId=getJingBeanBalanceDetail", page, new StringCallBack() {
            @Override
            public void onSuccess(@NonNull String result) {
                try {
                    //2021-10-05 10:18:37
                    JingDouBean jingDouBean = gson.fromJson(result, JingDouBean.class);
                    ArrayList<JingDouBean.DetailListDTO> dataList = jingDouBean.getDetailList();
                    boolean isFinish = true;
                    for (int i = 0; i < dataList.size(); i++) {
                        JingDouBean.DetailListDTO detail = dataList.get(i);
                        long beanDay = TimeUtil.INSTANCE.parseTime(detail.getDate());
                        if (beanDay < todayTime && beanDay > yesterdayTime) {
                            if (detail.getAmount() > 0) {
                                UserBean.INSTANCE.setAgo1Bean(UserBean.INSTANCE.getAgo1Bean() + detail.getAmount());
                            }
                        } else if (beanDay < yesterdayTime) {
                            isFinish = false;
                            break;
                        }
                    }
                    if (isFinish) {
                        page++;
                        get1AgoBeanData();
                    } else {
                        setData();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFail() {

            }
        });
    }

    private void getUserInfo() {
        HttpUtil.INSTANCE.getUserInfo(new StringCallBack() {
            @Override
            public void onSuccess(@NonNull String result) {
                try {
                    JSONObject job = new JSONObject(result);
                    try {
                        UserBean.INSTANCE.setNickName(job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("nickname"));
                        UserBean.INSTANCE.setUserLevel(job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("userLevel"));
                        UserBean.INSTANCE.setLevelName(job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("levelName"));
                        UserBean.INSTANCE.setHeadImageUrl(job.optJSONObject("data").optJSONObject("userInfo").optJSONObject("baseInfo").optString("headImageUrl"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        UserBean.INSTANCE.setBeanNum(job.optJSONObject("data").optJSONObject("assetInfo").optString("beanNum"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    setData();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onFail() {

            }
        });
    }

    private void getUserInfo1() {
        HttpUtil.INSTANCE.getUserInfo1(new StringCallBack() {
            @Override
            public void onSuccess(@NonNull String result) {
                Log.i("====", result);
                try {
                    JSONObject job = new JSONObject(result);
                    UserBean.INSTANCE.setJxiang(job.optJSONObject("user").optString("uclass").replace("京享值", ""));
                    setData();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onFail() {

            }
        });
    }

    private void setData() {
        if ("1".equals(CacheUtil.INSTANCE.getString("hideTips"))) {
            remoteViews.setViewVisibility(R.id.updateTime, View.GONE);
            remoteViews.setViewVisibility(R.id.tips, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.updateTime, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.tips, View.VISIBLE);
        }

        if ("1".equals(CacheUtil.INSTANCE.getString("hideNichen"))) {
            remoteViews.setTextViewText(R.id.nickName, "***");
        } else {
            remoteViews.setTextViewText(R.id.nickName, UserBean.INSTANCE.getNickName());
        }

        remoteViews.setTextViewText(R.id.beanNum, UserBean.INSTANCE.getBeanNum());
        remoteViews.setTextViewText(R.id.todayBean, "+" + UserBean.INSTANCE.getTodayBean());
        remoteViews.setTextViewText(R.id.todayBeanNum, UserBean.INSTANCE.getTodayBean() + "");
        remoteViews.setTextViewText(R.id.oneAgoBeanNum, UserBean.INSTANCE.getAgo1Bean() + "");
        remoteViews.setTextViewText(R.id.updateTime, "数据更新于:" + TimeUtil.INSTANCE.getCurrentData());
        remoteViews.setTextViewText(R.id.hongbao, UserBean.INSTANCE.getHb());
        remoteViews.setTextViewText(R.id.guoquHb, "今日过期:" + UserBean.INSTANCE.getGqhb());
        remoteViews.setTextViewText(R.id.jingXiang, UserBean.INSTANCE.getJxiang());

        if (TextUtils.isEmpty(UserBean.INSTANCE.getHeadImageUrl())) {
            Glide.with(MyApplication.mInstance)
                    .load(R.mipmap.icon_head_def)
                    .into(new SimpleTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            remoteViews.setImageViewBitmap(R.id.headImg, BitmapUtil.drawableToBitmap(resource));

                            AppWidgetManager manager = AppWidgetManager.getInstance(getApplicationContext());
                            ComponentName componentName = new ComponentName(getApplicationContext(), MyAppWidgetProvider.class);
                            manager.updateAppWidget(componentName, remoteViews);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            AppWidgetManager manager = AppWidgetManager.getInstance(getApplicationContext());
                            ComponentName componentName = new ComponentName(getApplicationContext(), MyAppWidgetProvider.class);
                            manager.updateAppWidget(componentName, remoteViews);
                        }
                    });
        } else {
            Glide.with(MyApplication.mInstance)
                    .load(UserBean.INSTANCE.getHeadImageUrl())
                    .into(new SimpleTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            remoteViews.setImageViewBitmap(R.id.headImg, BitmapUtil.drawableToBitmap(resource));

                            AppWidgetManager manager = AppWidgetManager.getInstance(getApplicationContext());
                            ComponentName componentName = new ComponentName(getApplicationContext(), MyAppWidgetProvider.class);
                            manager.updateAppWidget(componentName, remoteViews);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            AppWidgetManager manager = AppWidgetManager.getInstance(getApplicationContext());
                            ComponentName componentName = new ComponentName(getApplicationContext(), MyAppWidgetProvider.class);
                            manager.updateAppWidget(componentName, remoteViews);
                        }
                    });
        }
    }
}
