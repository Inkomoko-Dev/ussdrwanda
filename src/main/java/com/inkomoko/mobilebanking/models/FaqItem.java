package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Help / FAQ content shown under "5. Help" — editable through /api/admin/faqs
 * so Inkomoko can change it without a code release (ToR 3.1(vi)).
 */
@Entity
@Table(name = "faq_item")
@Getter
@Setter
@NoArgsConstructor
public class FaqItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISO 639 language code (ussd_language.code), e.g. "rw". */
    private String language;

    /** Short menu label, e.g. "Office locations". */
    private String title;

    /** Screen text shown when selected (keep ≤ ~150 chars to fit one USSD screen). */
    private String body;

    private Integer displayOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
