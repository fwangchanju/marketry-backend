package kr.co.marketry.common.cache;

/**
 * 캐시 보유 서비스 공통 계약. {@code T}는 각 구현이 캐싱하는 반환 타입.
 */
public interface CacheService<T> {

    /** 캐시된 데이터 반환(미스 시 적재 후 반환). */
    T getCache();

    /** 캐시 무효화. */
    void evict();
}
