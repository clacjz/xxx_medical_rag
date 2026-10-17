package com.clacjz.medical.rag.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.clacjz.medical.rag.config.mybatis.SqlDebugInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@MapperScan("com.clacjz.medical.rag.mapper")
public class MyBatisPlusConfig {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @PostConstruct
    public void init() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        SqlDebugInterceptor sqlDebugInterceptor = new SqlDebugInterceptor();
        interceptor.addInnerInterceptor(sqlDebugInterceptor);

        sqlSessionFactory.getConfiguration().addInterceptor(interceptor);

        log.info("MyBatisPlus interceptor registered: PaginationInnerInterceptor, SqlDebugInterceptor");
    }
}