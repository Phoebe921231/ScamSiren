package com.example.fp2.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * RiskRecord 的資料存取介面（DAO）
 */
@Dao
public interface RiskRecordDao {

    /**
     * 新增一筆風險紀錄
     */
    @Insert
    void insert(RiskRecordEntity record);

    /**
     * 取得所有歷史紀錄（最新的在前）
     */
    @Query(
            "SELECT * FROM risk_records " +
                    "ORDER BY createdAt DESC"
    )
    List<RiskRecordEntity> getAll();

    /**
     * 只取得中 / 高風險紀錄
     */
    @Query(
            "SELECT * FROM risk_records " +
                    "WHERE riskLevel IN ('MEDIUM', 'HIGH') " +
                    "ORDER BY createdAt DESC"
    )
    List<RiskRecordEntity> getMediumAndHighRisk();

    @Query("SELECT * FROM risk_records WHERE id = :id LIMIT 1")
    RiskRecordEntity getById(long id);
}
