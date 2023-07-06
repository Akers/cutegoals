/*
 * http_time_client.c
 *
 *  Created on: 2023年6月13日
 *      Author: Administrator
 */
#include "http_time_client.h"
#include "restclient-cpp/connection.h"
#include <string>

tm *HttpTimeClient::Client::get_http_time() {
	//发起HTTP GET请求
	RestClient::Response r = RestClient::get(this->url);
	std::string dateStr = r.headers["Date"];
	LOGD("dateStr: %s", dateStr.c_str());
	struct tm *timeinfo = TimeHelper::getDateTime();
	std::vector<std::string> splitedDate = this->split(dateStr, " ");
	std::vector<std::string> splitedTime = this->split(splitedDate[5], ":");
	int monNum = this->monNameMap[splitedDate[3]];
	std::string dayStr = splitedDate[2].size() >= 2 && splitedDate[2].rfind("0", 0) == 0 ? splitedDate[2].substr(1, 1) : splitedDate[2];
	std::string hourStr = splitedTime[1].size() >= 2 && splitedTime[1].rfind("0", 0) == 0 ? splitedTime[1].substr(1, 1) : splitedTime[1];
	std::string minStr = splitedTime[2].size() >= 2 && splitedTime[2].rfind("0", 0) == 0 ? splitedTime[2].substr(1, 1) : splitedTime[2];
	std::string secStr = splitedTime[3].size() >= 2 && splitedTime[3].rfind("0", 0) == 0 ? splitedTime[3].substr(1, 1) : splitedTime[3];

//	LOGD("http_date：%s-%d-%s %s:%s:%s\n", splitedDate[4].c_str(), monNum, dayStr.c_str()
//			, hourStr.c_str(), minStr.c_str(), secStr.c_str()
//	);

	timeinfo->tm_year = std::atoi(splitedDate[4].c_str());
	timeinfo->tm_mon = monNum;
	timeinfo->tm_mday = std::atoi(dayStr.c_str());
	if ("GMT" == splitedDate[6]) {
		// GMT 时间需要计算时区
		timeinfo->tm_hour = std::atoi(hourStr.c_str()) + 8;
	} else {
		timeinfo->tm_hour = std::atoi(hourStr.c_str());
	}
	timeinfo->tm_min = std::atoi(minStr.c_str());
	timeinfo->tm_sec = std::atoi(secStr.c_str());

//	LOGD("http_time：%d-%d-%d %d:%d:%d\n", timeinfo->tm_year, timeinfo->tm_mon, timeinfo->tm_mday
//			, timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec
//	);
	return timeinfo;
}


std::vector<std::string> HttpTimeClient::Client::split(const std::string& str, const std::string& delim) {
	std::vector<std::string> res;
	if("" == str) return res;
	std::string::size_type lastPos = str.find_first_not_of(delim, 0);
	std::string::size_type pos = str.find_first_not_of(delim, lastPos);
	while (std::string::npos != pos || std::string::npos != lastPos) {
		res.push_back(str.substr(lastPos, pos - lastPos));
		lastPos = str.find_first_not_of(delim, pos);
		pos = str.find_first_of(delim, lastPos);
	}
	return res;
}

void HttpTimeClient::Client::initMonNameMap() {
	this->monNameMap["Jan"] = 1;
	this->monNameMap["Feb"] = 2;
	this->monNameMap["Mar"] = 3;
	this->monNameMap["Apr"] = 4;
	this->monNameMap["May"] = 5;
	this->monNameMap["Jun"] = 6;
	this->monNameMap["Jul"] = 7;
	this->monNameMap["Aug"] = 8;
	this->monNameMap["Sept"] = 9;
	this->monNameMap["Oct"] = 10;
	this->monNameMap["Nov"] = 11;
	this->monNameMap["Dec"] = 12;
}

HttpTimeClient::Client::Client() {
	this->initMonNameMap();
}

HttpTimeClient::Client::~Client() {

}
