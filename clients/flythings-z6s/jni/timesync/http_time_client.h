/*
 * http_time_client.h
 *
 *  Created on: 2023年6月13日
 *      Author: Administrator
 */

#ifndef JNI_TIMESYNC_HTTP_TIME_CLIENT_H_
#define JNI_TIMESYNC_HTTP_TIME_CLIENT_H_

//#include "restclient-cpp/restclient.h"
//#include "curl/curl.h"
#include <time.h>
#include <errno.h>
#include <sys/time.h>
#include "utils/TimeHelper.h"
#include "utils/Log.h"
#include <vector>
#include <string>
#include <map>

namespace HttpTimeClient {
class Client {
public:
	/**
	 * 使用url获取互联网时间
	 */
	tm *get_http_time();
	Client();
	Client(char* url) : url(url){
		this->initMonNameMap();
	};
	~Client();
private:
	void initMonNameMap();
private:
	char* url;
	std::vector<std::string> split(const std::string& str, const std::string& delim);
	std::map<std::string, int> monNameMap;
};

}

#endif /* JNI_TIMESYNC_HTTP_TIME_CLIENT_H_ */
