package com.docuseeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisService {
    private final RedisTemplate<String, Object> m_redisConfig;

    private final ObjectMapper m_objectMapper;

    public String GetValue(String _strKey){
        Object obj = m_redisConfig.opsForValue().get(_strKey);;

        if(obj == null)
            return null;
        else
            return obj.toString();
    }

    public Boolean HasValue(String _strKey, String _strValue){
        if (!m_redisConfig.opsForList().getOperations().hasKey(_strKey)) {
            return false;
        }


        if(m_redisConfig.opsForList().indexOf(_strKey, _strValue) > 0)
            return true;
        else return false;
    }

    public void SetValue(String _strKey, Object value) {
        m_redisConfig.opsForValue().set(_strKey, value);
    }

    public Long RightPushValue(String _strKey, Object value) {
        return m_redisConfig.opsForList().rightPush(_strKey, value);
    }

    public Long LeftPushValue(String _strKey, Object value) {
        return m_redisConfig.opsForList().leftPush(_strKey, value);
    }

    public <T> T FirstValue(String _strKey, Class<T> clazz) {
        if(m_redisConfig.hasKey(_strKey)){

            return null;
        }else{
            Object obj = m_redisConfig.opsForList().getFirst(_strKey);

            if(obj != null) return m_objectMapper.convertValue(obj, clazz);
            else return null;
        }
    }

    public <T> List<T> GetAllListValues(String _strKey) {
        return (List<T>) m_redisConfig.opsForList().range(_strKey, 0, -1);
    }

    public <T> T RightPopValue(String _strKey, Class<T> clazz) {
        if(m_redisConfig.opsForList().size(_strKey) > 0){
            Object obj = m_redisConfig.opsForList().rightPop(_strKey);

            if(obj != null) return m_objectMapper.convertValue(obj, clazz);
            else return null;
        }else
            return null;
    }

    public <T> T LeftPopValue(String _strKey, Class<T> clazz) {
        if(m_redisConfig.opsForList().size(_strKey) > 0){
            Object obj = m_redisConfig.opsForList().leftPop(_strKey);

            if(obj != null) return m_objectMapper.convertValue(obj, clazz);
            else return null;
        }else
            return null;
    }

    public void DeleteValue(String _strKey) {
        m_redisConfig.delete(_strKey);
    }

    public Long RemoveListValue(String _strKey, String value) {

        return m_redisConfig.opsForList().remove(_strKey, 0, value);
    }

    public Boolean DeleteList(String _strKey){
        return m_redisConfig.opsForList().getOperations().delete(_strKey);
    }



//    private final ReactiveRedisOperations<String, Object> redisOps;
//    //private final ReactiveListOperations<String, Object> listOps;
//    private final ObjectMapper objectMapper;
//
//    public Mono<String> getValue(String key) {
//        return redisOps.opsForValue().get(key).map(String::valueOf);
//    }
//
//    public Mono<Boolean> setValue(String key, Object value) {
//        return redisOps.opsForValue().set(key, value);
//    }
//
//    public Mono<Long> pushValue(String key, Object value) {
//        return redisOps.opsForList().rightPush(key, value);
//    }
//
//    public <T> Mono<T>  rightPopValue(String key, Class<T> clazz) {
//        return redisOps.opsForList().rightPop(key).switchIfEmpty(Mono.error(new RuntimeException("No Datas for key: " + key)))
//                .flatMap(value -> Mono.just(objectMapper.convertValue(value, clazz)));
//    }
//
//    public <T> Mono<T>  leftPopValue(String key, Class<T> clazz) {
//        return redisOps.opsForList().leftPop(key).switchIfEmpty(Mono.error(new RuntimeException("No Datas for key: " + key)))
//                .flatMap(value -> Mono.just(objectMapper.convertValue(value, clazz)));
//    }
//
//    public <T> Mono<T> firstValue(String key, Class<T> clazz) {
//        return redisOps.opsForList().getFirst(key).switchIfEmpty(Mono.error(new RuntimeException("No Datas for key: " + key)))
//                .flatMap(value -> Mono.just(objectMapper.convertValue(value, clazz)));
//    }
//
//
//
//    public <T> Mono<T> getCacheValueGeneric(String key, Class<T> clazz) {
//        try {
//            return redisOps.opsForList().leftPop(key)
//                    .switchIfEmpty(Mono.error(new RuntimeException("No Datas for key: " + key)))
//                    .flatMap(value -> Mono.just(objectMapper.convertValue(value, clazz)));
//        } catch (Exception e) {
//            e.getStackTrace();
//            return Mono.error(new RuntimeException("error occured!" + e.getMessage()));
//        }
//    }

}
