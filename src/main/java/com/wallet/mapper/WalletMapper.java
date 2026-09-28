package com.wallet.mapper;

import com.wallet.dto.WalletResponse;
import com.wallet.model.Wallet;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    WalletResponse toResponse(Wallet wallet);
}
