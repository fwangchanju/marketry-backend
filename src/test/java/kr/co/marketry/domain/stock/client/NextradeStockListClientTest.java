package kr.co.marketry.domain.stock.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.co.marketry.domain.stock.dto.NextradeStockListResponse.Item;
import org.junit.jupiter.api.Test;

class NextradeStockListClientTest {

    @Test
    void 편입_상태인_종목의_6자리_코드만_모은다() {
        List<Item> items = List.of(
                new Item("A005930", "20250324", "편입"),
                new Item("A488280", "20260930", "편출"),
                new Item("A000660", "20250324", "편입"));

        assertThat(NextradeStockListClient.activeStockCodes(items)).containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    void 같은_종목이_여러_번_나오면_가장_마지막_일자의_상태를_쓴다() {
        List<Item> items = List.of(
                new Item("A005930", "20260101", "편출"),
                new Item("A005930", "20250324", "편입"),
                new Item("A000660", "20250324", "편입"),
                new Item("A000660", "20260101", "편출"),
                new Item("A000660", "20260601", "편입"));

        assertThat(NextradeStockListClient.activeStockCodes(items)).containsExactlyInAnyOrder("000660");
    }

    @Test
    void 코드나_상태가_없는_항목은_무시한다() {
        List<Item> items = List.of(new Item(null, "20250324", "편입"), new Item("A005930", "20250324", null));

        assertThat(NextradeStockListClient.activeStockCodes(items)).isEmpty();
    }
}
