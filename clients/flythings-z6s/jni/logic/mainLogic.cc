#pragma once
#include "uart/ProtocolSender.h"
#include <time.h>
#include "utils/TimeHelper.h"
#include "utils/Log.h"
#include <errno.h>
#include <sys/time.h>
#include "../timesync/ntp_client.h"
#include "../timesync/xtime.h"
#include "../timesync/http_time_client.h"
#include "restclient-cpp/restclient.h"
#include "../curl/curl.h"
#include <string>

/*
*此文件由GUI工具生成
*文件功能：用于处理用户的逻辑相应代码
*功能说明：
*========================onButtonClick_XXXX
当页面中的按键按下后系统会调用对应的函数，XXX代表GUI工具里面的[ID值]名称，
如Button1,当返回值为false的时候系统将不再处理这个按键，返回true的时候系统将会继续处理此按键。比如SYS_BACK.
*========================onSlideWindowItemClick_XXXX(int index) 
当页面中存在滑动窗口并且用户点击了滑动窗口的图标后系统会调用此函数,XXX代表GUI工具里面的[ID值]名称，
如slideWindow1;index 代表按下图标的偏移值
*========================onSeekBarChange_XXXX(int progress) 
当页面中存在滑动条并且用户改变了进度后系统会调用此函数,XXX代表GUI工具里面的[ID值]名称，
如SeekBar1;progress 代表当前的进度值
*========================ogetListItemCount_XXXX() 
当页面中存在滑动列表的时候，更新的时候系统会调用此接口获取列表的总数目,XXX代表GUI工具里面的[ID值]名称，
如List1;返回值为当前列表的总条数
*========================oobtainListItemData_XXXX(ZKListView::ZKListItem *pListItem, int index)
 当页面中存在滑动列表的时候，更新的时候系统会调用此接口获取列表当前条目下的内容信息,XXX代表GUI工具里面的[ID值]名称，
如List1;pListItem 是贴图中的单条目对象，index是列表总目的偏移量。具体见函数说明
*========================常用接口===============
*LOGD(...)  打印调试信息的接口
*mTextXXXPtr->setText("****") 在控件TextXXX上显示文字****
*mButton1Ptr->setSelected(true); 将控件mButton1设置为选中模式，图片会切换成选中图片，按钮文字会切换为选中后的颜色
*mSeekBarPtr->setProgress(12) 在控件mSeekBar上将进度调整到12
*mListView1Ptr->refreshListView() 让mListView1 重新刷新，当列表数据变化后调用
*mDashbroadView1Ptr->setTargetAngle(120) 在控件mDashbroadView1上指针显示角度调整到120度
*
* 在Eclipse编辑器中  使用 “alt + /”  快捷键可以打开智能提示
*/
static int MAX_SCORE = 10;
static int MIN_SCORE = 0;
static bool SCORE_CYCLE_CHANGE = false;
static int SCORE = 0;
static bool AUTO_TIME_SYNC = true;

char* xszt_host[] =
{
    "ntp.tencent.com" ,
    "ntp1.tencent.com",
    "ntp2.tencent.com",
    "ntp3.tencent.com",
    "ntp4.tencent.com",
    "ntp5.tencent.com",
    "ntp1.aliyun.com" ,
    "ntp2.aliyun.com" ,
    "ntp3.aliyun.com" ,
    "ntp4.aliyun.com" ,
    "ntp5.aliyun.com" ,
    "ntp6.aliyun.com" ,
    "ntp7.aliyun.com" ,
    "time.edu.cn"     ,
    "s2c.time.edu.cn" ,
    "s2f.time.edu.cn" ,
    "s2k.time.edu.cn"
};

static int NTP_XUT_PORT = 123;
static int NTP_XUT_REPT = 1;
static int NTP_XUT_TMOUT = 3000;

/**
 * 注册定时器
 * 填充数组用于注册定时器
 * 注意：id不能重复
 */
static S_ACTIVITY_TIMEER REGISTER_ACTIVITY_TIMER_TAB[] = {
	//{0,  6000}, //定时器id=0, 时间间隔6秒
	// 时间同步定时器 12 小时一次
	{1,  12 * 60 * 60 * 1000},
	{100, 1000},
//	{1,  5 * 60 * 1000},
};

static bool scoreChanged() {
	if (SCORE > 0) {
		char path[50] = {0};
		snprintf(path, sizeof(path), "Carrot%d.png", SCORE);
		mTextViewScoresPtr->setBackgroundPic(path);
	} else {
		mTextViewScoresPtr->setBackgroundPic("");
	}

	return true;
}

static int addScore(int score) {
	SCORE += score;
	if (SCORE > MAX_SCORE) {
		if (SCORE_CYCLE_CHANGE) {
			SCORE = MIN_SCORE;
		} else {
			SCORE = MAX_SCORE;
		}
	}
	scoreChanged();
	return SCORE;
}

static int addScore() {
	return addScore(1);
}

static int decreaseScore(int score) {
	SCORE -= score;
	if (SCORE < MIN_SCORE) {
		if (SCORE_CYCLE_CHANGE) {
			SCORE = MAX_SCORE;
		} else {
			SCORE = MIN_SCORE;
		}
	}
	scoreChanged();
	return SCORE;
}

static int decreaseScore() {
	return decreaseScore(1);
}

static int updateSystemDateTime() {
	mTextViewMainMessagePtr->setText("系统时间同步中...");
	xtime_vnsec_t xtm_ltime = XTIME_INVALID_VNSEC;
	xtime_descr_t xtm_descr = { 0 };

	// 1. 先获取互联网时间
	HttpTimeClient::Client c = HttpTimeClient::Client("http://www.baidu.com");
	tm *httpTime = c.get_http_time();
	// 2. 使用互联网时间作为基准时间
	httpTime->tm_year = httpTime->tm_year - 1900;
	httpTime->tm_mon = httpTime->tm_mon - 1;
	TimeHelper::setDateTime(httpTime);
	// 3. 获取ntp时间作为校准
	xtime_vnsec_t xvt = ntpcli_get_time(xszt_host[0], NTP_XUT_PORT, NTP_XUT_TMOUT);
	if (XTMVNSEC_IS_VALID(xvt)) {
		// 转换为时间描述
		xtm_descr = time_vtod(xvt);
//		LOGD("同步成功, xtm_time: %d\n", xtm_descr.xtm_time);
//		LOGD("ntp_time：%d-%d-%d %d:%d:%d\n", (int)xtm_descr.ctx_year, (int)xtm_descr.ctx_month, (int)xtm_descr.ctx_day
//				,(int)xtm_descr.ctx_hour, (int)xtm_descr.ctx_minute, (int)xtm_descr.ctx_second
//		);

		tm *pTm = TimeHelper::getDateTime();
		pTm->tm_year = (int)xtm_descr.ctx_year - 1900;
		pTm->tm_mon = (int)xtm_descr.ctx_month - 1;
		pTm->tm_mday = (int)xtm_descr.ctx_day;
		// 转换为东8
		pTm->tm_hour = (int)xtm_descr.ctx_hour + 8;
		pTm->tm_min = (int)xtm_descr.ctx_minute;
		pTm->tm_sec = (int)xtm_descr.ctx_second;
//		LOGD("ntp_tm：%d-%d-%d %d:%d:%d\n", pTm->tm_year, pTm->tm_mon, pTm->tm_mday
//					, pTm->tm_hour, pTm->tm_min, pTm->tm_sec
//			);
		TimeHelper::setDateTime(pTm);
//		tm *pTm_new = TimeHelper::getDateTime();
//		LOGD("local_time：%d-%d-%d %d:%d:%d\n", pTm_new->tm_year, pTm_new->tm_mon, pTm_new->tm_mday
//				, pTm_new->tm_hour, pTm_new->tm_min, pTm_new->tm_sec
//		);

//		xtm_ltime = time_vnsec();
//		LOGD("\tDeviation    : %lld us\n", ((x_int64_t)(xtm_ltime - xvt)) / 10LL);
	} else {
		// NTP获取失败

	}
	mTextViewMainMessagePtr->setText("");
	return 0;
}

/**
 * 当界面构造时触发
 */
static void onUI_init(){
    //Tips :添加 UI初始化的显示代码到这里,如:mText1Ptr->setText("123");
	EASYUICONTEXT->hideStatusBar();
	TimeHelper::setDateTime("2023-06-13 15:55:00");
	updateSystemDateTime();
}

/**
 * 当切换到该界面时触发
 */
static void onUI_intent(const Intent *intentPtr) {
    if (intentPtr != NULL) {
        //TODO
    	EASYUICONTEXT->hideStatusBar();
    }
}

/*
 * 当界面显示时触发
 */
static void onUI_show() {
	EASYUICONTEXT->hideStatusBar();
}

/*
 * 当界面隐藏时触发
 */
static void onUI_hide() {

}

/*
 * 当界面完全退出时触发
 */
static void onUI_quit() {

}

/**
 * 串口数据回调接口
 */
static void onProtocolDataUpdate(const SProtocolData &data) {

}

/**
 * 定时器触发函数
 * 不建议在此函数中写耗时操作，否则将影响UI刷新
 * 参数： id
 *         当前所触发定时器的id，与注册时的id相同
 * 返回值: true
 *             继续运行当前定时器
 *         false
 *             停止运行当前定时器
 */
static bool onUI_Timer(int id){
	switch (id) {
		case 1:
			updateSystemDateTime();
			break;
		default:
			break;
	}
    return true;
}

/**
 * 有新的触摸事件时触发
 * 参数：ev
 *         新的触摸事件
 * 返回值：true
 *            表示该触摸事件在此被拦截，系统不再将此触摸事件传递到控件上
 *         false
 *            触摸事件将继续传递到控件上
 */
static bool onmainActivityTouchEvent(const MotionEvent &ev) {
    switch (ev.mActionStatus) {
		case MotionEvent::E_ACTION_DOWN://触摸按下
			//LOGD("时刻 = %ld 坐标  x = %d, y = %d", ev.mEventTime, ev.mX, ev.mY);
			break;
		case MotionEvent::E_ACTION_MOVE://触摸滑动
			break;
		case MotionEvent::E_ACTION_UP:  //触摸抬起
			break;
		default:
			break;
	}
	return false;
}
static bool onButtonClick_ButtonHappy(ZKButton *pButton) {
    LOGD(" ButtonClick ButtonHappy !!!\n");
    addScore(1);
    return false;
}

static bool onButtonClick_ButtonCry(ZKButton *pButton) {
    LOGD(" ButtonClick ButtonCry !!!\n");
    decreaseScore(1);
    return false;
}

static bool onButtonClick_ButtonSettings(ZKButton *pButton) {
    LOGD(" ButtonClick ButtonSettings !!!\n");
    EASYUICONTEXT->openActivity("settingsActivity");
    return false;
}
//static bool onButtonClick_ButtonGoToTimer(ZKButton *pButton) {
//    LOGD(" ButtonClick ButtonGoToTimer !!!\n");
//
//    Intent *pIntent = new Intent();
//    pIntent->putExtra("score", std::to_string(SCORE));
//    EASYUICONTEXT->openActivity("timerActivity", pIntent);
//    return false;
//}

static bool onButtonClick_ButtonTV(ZKButton *pButton) {
    LOGD(" ButtonClick ButtonTV !!!\n");
    //TODO: 点击电视图标后消耗积分开始计时
    // 1. 向后端发送积分扣除申请
    // 2. 判断后端积分扣除情况
    return false;
}

static bool onButtonClick_ButtonCloseWindowDuihuan(ZKButton *pButton) {
    LOGD(" ButtonClick ButtonCloseWindowDuihuan !!!\n");
    mWindowDuihuanPtr->hideWnd();
    return false;
}
static bool onButtonClick_ButtonLuobo(ZKButton *pButton) {
    LOGD(" ButtonClick ButtonLuobo !!!\n");
    mWindowDuihuanPtr->showWnd();
    return false;
}
