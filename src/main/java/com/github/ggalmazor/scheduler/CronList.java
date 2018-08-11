package com.github.ggalmazor.scheduler;

import static com.cronutils.model.field.CronFieldName.DAY_OF_MONTH;
import static com.cronutils.model.field.CronFieldName.DAY_OF_WEEK;
import static com.cronutils.model.field.CronFieldName.DAY_OF_YEAR;
import static com.cronutils.model.field.CronFieldName.HOUR;
import static com.cronutils.model.field.CronFieldName.MINUTE;
import static com.cronutils.model.field.CronFieldName.MONTH;
import static com.cronutils.model.field.CronFieldName.SECOND;
import static com.cronutils.model.field.CronFieldName.YEAR;
import static com.cronutils.model.field.expression.FieldExpressionFactory.on;
import static java.util.stream.Collectors.joining;

import com.cronutils.builder.CronBuilder;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.field.expression.On;
import com.cronutils.parser.CronParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class CronList {
  private static final CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
  private static final CronDescriptor cronDescriptor = CronDescriptor.instance(Locale.US);

  public static void main(String[] args) throws IOException, InterruptedException {
    List<Job> jobs = runCron();
    jobs.forEach(System.out::println);
    Job job = jobs.get(jobs.size()-1);
    jobs.add(job.copy());
    install(jobs);
    return;
  }

  private static void install(List<Job> jobs) throws IOException, InterruptedException {
    Path tempFile = Files.createTempFile("scheduler", "crontab");
    String collect = jobs.stream().map(Job::render).collect(joining("\n")) + "\n";
    Files.write(tempFile, collect.getBytes());
    System.out.println("New cron at " + tempFile + " with contents:");
    Files.readAllLines(tempFile).forEach(System.out::println);
    Process p = new ProcessBuilder("crontab", tempFile.toString()).start();
    p.waitFor();
    Files.delete(tempFile);
    if (p.exitValue() != 0)
      throw new RuntimeException("Command errored");
  }

  private static List<Job> runCron() throws IOException, InterruptedException {
    Process p = new ProcessBuilder("crontab", "-l").start();
    p.waitFor();
    try (InputStream stdOutIs = p.getInputStream();
         InputStreamReader stdOutIsr = new InputStreamReader(stdOutIs);
         java.io.BufferedReader stdOurReader = new java.io.BufferedReader(stdOutIsr)
    ) {
      List<String> lines = new ArrayList<>();
      String s;
      while ((s = stdOurReader.readLine()) != null)
        lines.add(s);
      return lines.stream()
          .filter(line -> !line.startsWith("#"))
          .map(Job::from)
          .collect(Collectors.toList());
    }
  }

  static class Job {
    private final Cron cron;
    private final String command;

    Job(Cron cron, String command) {
      this.cron = cron;
      this.command = command;
    }

    static Job from(String cronLine) {
      List<String> parts = Arrays.asList(cronLine.split(" "));
      return new Job(
          cronParser.parse(parts.subList(0, 5).stream().collect(joining(" "))),
          parts.subList(5, parts.size()).stream().collect(joining(" "))
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      Job job = (Job) o;
      return Objects.equals(cron, job.cron) &&
          Objects.equals(command, job.command);
    }

    @Override
    public int hashCode() {

      return Objects.hash(cron, command);
    }

    @Override
    public String toString() {
      return "Run " + command + " " + cronDescriptor.describe(cron);
    }

    public Job copy() {
      CronBuilder builder = CronBuilder.cron(cron.getCronDefinition());
      cron.retrieveFieldsAsMap().values().forEach(field -> {
        switch (field.getField()) {
          case SECOND:
            builder.withSecond(cron.retrieve(SECOND).getExpression());
            break;
          case MINUTE:
            builder.withMinute(cron.retrieve(MINUTE).getExpression());
            break;
          case HOUR:
            builder.withHour(cron.retrieve(HOUR).getExpression());
            break;
          case DAY_OF_MONTH:
            builder.withDoM(cron.retrieve(DAY_OF_MONTH).getExpression());
            break;
          case MONTH:
            builder.withMonth(cron.retrieve(MONTH).getExpression());
            break;
          case DAY_OF_WEEK:
            builder.withDoW(cron.retrieve(DAY_OF_WEEK).getExpression());
            break;
          case YEAR:
            builder.withYear(cron.retrieve(YEAR).getExpression());
            break;
          case DAY_OF_YEAR:
            builder.withDoY(cron.retrieve(DAY_OF_YEAR).getExpression());
            break;
        }
      });
      return new Job(builder.withHour(on(((On) cron.retrieve(HOUR).getExpression()).getTime().getValue() + 1)).instance(), command);
    }

    public String render() {
      return cron.asString() + " " + command;
    }
  }
}
