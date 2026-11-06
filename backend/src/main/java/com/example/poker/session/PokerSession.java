package com.example.poker.session;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import com.example.poker.gitlab.GitLabIssue;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "poker_session")
public class PokerSession extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SessionState state;

    @Column(name = "selected_issue_iid")
    public Integer selectedIssueIid;

    @Column(name = "issue_title")
    public String issueTitle;

    @Column(name = "issue_description")
    public String issueDescription;

    @Column(name = "issue_web_url")
    public String issueWebUrl;

    @Column(name = "issue_labels")
    public String[] issueLabels;

    @Column(name = "current_round_id")
    public UUID currentRoundId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public void selectIssue(GitLabIssue issue) {
        selectedIssueIid = issue.iid();
        issueTitle = issue.title();
        issueDescription = issue.description();
        issueWebUrl = issue.webUrl();
        issueLabels = issue.labels() == null ? new String[0] : issue.labels().toArray(String[]::new);
    }
}
