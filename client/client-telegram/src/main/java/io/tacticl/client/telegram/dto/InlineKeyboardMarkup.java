package io.tacticl.client.telegram.dto;

import java.util.List;

public record InlineKeyboardMarkup(List<List<InlineKeyboardButton>> inline_keyboard) {}
