package com.moae.client.dto;

public record JiraTicketResponse(
    String issueKey,
    String summary,
    String description,
    String status,
    String assignee
) {}
