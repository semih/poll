package com.challenge.poll.service;

import com.challenge.poll.model.ChoiceVoteCount;
import com.challenge.poll.model.jpa.Choice;
import com.challenge.poll.model.jpa.Poll;
import com.challenge.poll.model.jpa.User;
import com.challenge.poll.model.jpa.Vote;
import com.challenge.poll.payload.request.PollRequest;
import com.challenge.poll.payload.request.VoteRequest;
import com.challenge.poll.payload.response.PollResponse;
import com.challenge.poll.repository.PollRepository;
import com.challenge.poll.repository.UserRepository;
import com.challenge.poll.repository.VoteRepository;
import com.challenge.poll.security.CustomUserDetails;
import com.challenge.poll.util.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PollService {

    @Autowired
    private PollRepository pollRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(PollService.class);

    public List<PollResponse> getAllPolls() {

        List<Poll> polls = pollRepository.findAll();
        List<Long> pollIds =new ArrayList<Long>();
        polls.forEach(poll -> { pollIds.add(poll.getId()); });

        Map<Long, Long> choiceVoteCountMap = getChoiceVoteCountMap(pollIds);
        List<PollResponse> pollResponses = new ArrayList<PollResponse>();

        polls.forEach(poll -> {
            PollResponse pollResponse = ModelMapper.mapPollToPollResponse( poll, choiceVoteCountMap);
            pollResponses.add(pollResponse);
        });

        return pollResponses;
    }

    public Poll createPoll(PollRequest pollRequest) {
        Poll poll = new Poll();
        poll.setQuestion(pollRequest.getQuestion());

        pollRequest.getChoices().forEach(choiceRequest -> {
            poll.addChoice(new Choice(choiceRequest.getText()));
        });

        return pollRepository.save(poll);
    }

    public PollResponse getPollById(Long pollId) {
        Poll poll = pollRepository.findById(pollId).orElseThrow(() -> new RuntimeException(String.format("Poll not found with id : '%s'", pollId)));
        List<ChoiceVoteCount> votes = voteRepository.countByPollIdGroupByChoiceId(pollId);
        Map<Long, Long> choiceVotesMap = votes.stream().collect(Collectors.toMap(ChoiceVoteCount::getChoiceId, ChoiceVoteCount::getVoteCount));
        PollResponse pollResponse = new PollResponse();
        return ModelMapper.mapPollToPollResponse(poll, choiceVotesMap);
    }

    public PollResponse castVoteAndGetUpdatedPoll(Long pollId, VoteRequest voteRequest, CustomUserDetails currentUser) {
        Poll poll = pollRepository.findById(pollId).orElseThrow(() -> new RuntimeException(String.format("Poll not found with id : '%s'", pollId)));
        User user = userRepository.getOne(currentUser.getId());

        Choice selectedChoice = poll.getChoices().stream()
                .filter(choice -> choice.getId().equals(voteRequest.getChoiceId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Choice not found with id : '%s'", voteRequest.getChoiceId())));

        Vote vote = new Vote();
        vote.setPoll(poll);
        vote.setUser(user);
        vote.setChoice(selectedChoice);

        try {
            vote = voteRepository.save(vote);
        } catch (DataIntegrityViolationException ex) {
            logger.info("User {} has already voted in Poll {}", currentUser.getId(), pollId);
            throw new RuntimeException("Sorry! You have already cast your vote in this poll");
        }

        List<ChoiceVoteCount> votes = voteRepository.countByPollIdGroupByChoiceId(pollId);
        Map<Long, Long> choiceVotesMap = votes.stream().collect(Collectors.toMap(ChoiceVoteCount::getChoiceId, ChoiceVoteCount::getVoteCount));
        return ModelMapper.mapPollToPollResponse(poll, choiceVotesMap);
    }

    private Map<Long, Long> getChoiceVoteCountMap(List<Long> pollIds) {
        List<ChoiceVoteCount> votes = voteRepository.countByPollIdInGroupByChoiceId(pollIds);
        Map<Long, Long> choiceVotesMap = votes.stream().collect(Collectors.toMap(ChoiceVoteCount::getChoiceId, ChoiceVoteCount::getVoteCount));
        return choiceVotesMap;
    }

    public void deletePoll(Long pollId) {
        pollRepository.deleteById(pollId);
    }

    public Poll updatePoll(Long pollId, PollRequest pollRequest) {
        Optional<Poll> optionalPoll = pollRepository.findById(pollId);
        Poll poll = optionalPoll.get();

        poll.getChoices().removeAll(poll.getChoices());
        poll.setQuestion(pollRequest.getQuestion());

        pollRequest.getChoices().forEach(choiceRequest -> {
            poll.addChoice(new Choice(choiceRequest.getText()));
        });

        return pollRepository.save(poll);
    }
}