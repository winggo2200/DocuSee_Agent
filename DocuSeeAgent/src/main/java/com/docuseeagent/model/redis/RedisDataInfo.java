package com.docuseeagent.model.redis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
public class RedisDataInfo {
    public String status;
    public String date;

    public RedisDataInfo(String date) {
        this.status = "None";
        this.date = date;
    }

    public RedisDataInfo(String status, String date) {
        this.status = status;
        this.date = date;
    }

    public Date GetDateTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return formatter.parse(this.date);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
