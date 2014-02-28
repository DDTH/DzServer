package controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import play.libs.F.Function0;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import com.github.ddth.frontapi.ApiResult;
import com.github.ddth.frontapi.internal.JsonUtils;

import dzserver.Global;

public class RestApi extends Controller {

    /*
     * Handle GET:/api/<auth_key>/<module>/<api>?...
     */
    public static Promise<Result> get(final String authKey, final String module, final String api) {
        Promise<Result> promise = Promise.promise(new Function0<Result>() {
            public Result apply() throws Exception {
                Map<String, Object> inputs = new HashMap<String, Object>();
                for (Entry<String, String[]> entry : request().queryString().entrySet()) {
                    String key = entry.getKey();
                    String[] value = entry.getValue();
                    if (value != null) {
                        if (value.length > 1) {
                            inputs.put(key, value);
                        } else if (value.length > 0) {
                            inputs.put(key, value[0]);
                        }
                    }
                }

                ApiResult apiResult = Global.apiRegistry.callApi(module, api, authKey, inputs);
                return ok(Json.toJson(apiResult));
            }
        });
        return promise;
    }

    /*
     * Handle POST:/api/<auth_key>/<module>/<api>?...
     */
    public static Promise<Result> post(final String authKey, final String module, final String api) {
        Promise<Result> promise = Promise.promise(new Function0<Result>() {
            public Result apply() throws Exception {
                Object inputs = JsonUtils.fromJsonString(request().body().asText());
                ApiResult apiResult = Global.apiRegistry.callApi(module, api, authKey, inputs);
                return ok(Json.toJson(apiResult));
            }
        });
        return promise;
    }
}
