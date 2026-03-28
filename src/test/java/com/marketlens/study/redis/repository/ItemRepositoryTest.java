package com.marketlens.study.redis.repository;

import com.marketlens.study.redis.dto.Item;
import com.marketlens.study.redis.dto.ItemCreateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;


class ItemRepositoryTest {
    ItemRepository itemRepository = new ItemRepository();

    @Test
    void readAll() {

        // given
        List<Item> items =IntStream.range(0, 3)
            .mapToObj(idx -> itemRepository.create(Item.create(new ItemCreateRequest("data" + idx))))
            .toList();

        // when
        List<Item> firstPage = itemRepository.readAll(1L, 2L);
        List<Item> secondPage = itemRepository.readAll(2L, 2L);

        // then
        assertThat(firstPage).hasSize(2);

        assertThat(firstPage.get(0).getItemId()).isEqualTo(items.get(2).getItemId());
        assertThat(firstPage.get(1).getItemId()).isEqualTo(items.get(1).getItemId());

        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getItemId()).isEqualTo(items.get(0).getItemId());
    }


    @Test
    void readAllInifiniteScroll() {
        // given
        List<Item> items =IntStream.range(0, 3)
                .mapToObj(idx -> itemRepository.create(Item.create(new ItemCreateRequest("data" + idx))))
                .toList();

        // when
        List<Item> firstPage = itemRepository.readAllInfiniteScroll(null, 2L);
        List<Item> secondPage = itemRepository.readAllInfiniteScroll(firstPage.getLast().getItemId(), 2L);

        // then
        assertThat(firstPage).hasSize(2);

        assertThat(firstPage.get(0).getItemId()).isEqualTo(items.get(2).getItemId());
        assertThat(firstPage.get(1).getItemId()).isEqualTo(items.get(1).getItemId());

        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getItemId()).isEqualTo(items.get(0).getItemId());

    }
}
