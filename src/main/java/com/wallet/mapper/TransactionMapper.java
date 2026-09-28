package com.wallet.mapper;

import com.wallet.dto.TransactionResponse;
import com.wallet.model.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionResponse toResponse(Transaction transaction);
}
