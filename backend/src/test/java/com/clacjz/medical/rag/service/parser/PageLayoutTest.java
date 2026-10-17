package com.clacjz.medical.rag.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageLayout 单元测试")
class PageLayoutTest {

    @Nested
    @DisplayName("PageLayout record - 页面布局信息")
    class PageLayoutRecordTests {

        @Test
        @DisplayName("创建页面布局")
        void shouldCreatePageLayout() {
            PageLayout layout = new PageLayout(1, "第一页内容", 0, 100);
            assertEquals(1, layout.pageNumber());
            assertEquals("第一页内容", layout.text());
            assertEquals(0, layout.startPos());
            assertEquals(100, layout.endPos());
        }

        @Test
        @DisplayName("使用builder创建")
        void shouldBuildPageLayout() {
            PageLayout layout = PageLayout.builder()
                    .pageNumber(2)
                    .text("第二页内容")
                    .startPos(100)
                    .endPos(200)
                    .build();

            assertEquals(2, layout.pageNumber());
            assertEquals("第二页内容", layout.text());
            assertEquals(100, layout.startPos());
            assertEquals(200, layout.endPos());
        }

        @Test
        @DisplayName("多页布局列表")
        void shouldCreateMultiplePageLayouts() {
            List<PageLayout> layouts = List.of(
                    new PageLayout(1, "第一页", 0, 100),
                    new PageLayout(2, "第二页", 100, 200),
                    new PageLayout(3, "第三页", 200, 300)
            );

            assertEquals(3, layouts.size());
            assertEquals("第一页", layouts.get(0).text());
            assertEquals("第二页", layouts.get(1).text());
            assertEquals("第三页", layouts.get(2).text());
        }

        @Test
        @DisplayName("页码从1开始")
        void shouldStartFromPageOne() {
            PageLayout layout = PageLayout.builder()
                    .pageNumber(1)
                    .text("内容")
                    .startPos(0)
                    .endPos(50)
                    .build();
            assertEquals(1, layout.pageNumber());
        }

        @Test
        @DisplayName("空页面内容")
        void shouldHandleEmptyText() {
            PageLayout layout = new PageLayout(1, "", 0, 0);
            assertEquals("", layout.text());
            assertEquals(0, layout.startPos());
            assertEquals(0, layout.endPos());
        }
    }
}