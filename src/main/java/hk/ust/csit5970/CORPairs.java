package hk.ust.csit5970;

import org.apache.commons.cli.*;
import org.apache.commons.cli.Options;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Compute the bigram count using "pairs" approach
 */
public class CORPairs extends Configured implements Tool {
	private static final Logger LOG = Logger.getLogger(CORPairs.class);

	/*
	 * TODO: Write your first-pass Mapper here.
	 */
	private static class CORMapper1 extends
			Mapper<LongWritable, Text, Text, IntWritable> {
		@Override
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			HashMap<String, Integer> word_set = new HashMap<String, Integer>();
			// Please use this tokenizer! DO NOT implement a tokenizer by yourself!
			String clean_doc = value.toString().replaceAll("[^a-z A-Z]", " ");
			StringTokenizer doc_tokenizer = new StringTokenizer(clean_doc);
			/*
			 * TODO: Your implementation goes here.
			 */
			while (doc_tokenizer.hasMoreTokens()) {
		        	String word = doc_tokenizer.nextToken();
		        	if (word_set.containsKey(word)) {
		            	word_set.put(word, word_set.get(word) + 1);
		        	} else {
		            	word_set.put(word, 1);
		        	}
			}

			for (Map.Entry<String, Integer> single_entry : word_set.entrySet()) {
				context.write(new Text(single_entry.getKey()), new IntWritable(single_entry.getValue()));
			}
		}
	}

	/*
	 * TODO: Write your first-pass reducer here.
	 */
	private static class CORReducer1 extends
			Reducer<Text, IntWritable, Text, IntWritable> {
		@Override
		public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
			/*
			 * TODO: Your implementation goes here.
			 */
			int sum_count = 0;
			for (IntWritable value : values) {
				sum_count += value.get();
		    }
		    context.write(key, new IntWritable(sum_count));
		}
	}


	/*
	 * TODO: Write your second-pass Mapper here.
	 */
	public static class CORPairsMapper2 extends Mapper<LongWritable, Text, PairOfStrings, IntWritable> {
		private final static IntWritable ONE = new IntWritable(1);
	    private final static PairOfStrings pair = new PairOfStrings();
		
		@Override
		protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			// Please use this tokenizer! DO NOT implement a tokenizer by yourself!
			StringTokenizer doc_tokenizer = new StringTokenizer(value.toString().replaceAll("[^a-z A-Z]", " "));
			/*
			 * TODO: Your implementation goes here.
			 */
			HashSet<String> unique_Words = new HashSet<String>();
			while (doc_tokenizer.hasMoreTokens()) {
				unique_Words.add(doc_tokenizer.nextToken());
			}
			String[] words = unique_Words.toArray(new String[0]);

			for (int x = 0; x < words.length; x++) {
				for (int y = x + 1; y < words.length; y++) {
					String word_1 = words[x];
					String word_2 = words[y];
					//Compare to see which one go first
					String first;
					String second;

					if (word_1.compareTo(word_2) < 0) {
						first = word_1;
						second = word_2;
					} else {
						first = word_2;
						second = word_1;
					}
					//emit the pair
					pair.set(first,second);
					context.write(pair, ONE);
				}
			}  
		}
	}

	/*
	 * TODO: Write your second-pass Combiner here.
	 */
	private static class CORPairsCombiner2 extends Reducer<PairOfStrings, IntWritable, PairOfStrings, IntWritable> {
		@Override
		protected void reduce(PairOfStrings key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
			/*
			 * TODO: Your implementation goes here.
			 */
			int sum_count = 0;
			for (IntWritable value : values) {
				sum_count += value.get(); // Sum up counts
			}
			context.write(key, new IntWritable(sum_count));
		}
	}

	/*
	 * TODO: Write your second-pass Reducer here.
	 */
	public static class CORPairsReducer2 extends Reducer<PairOfStrings, IntWritable, PairOfStrings, DoubleWritable> {
		private final static Map<String, Integer> word_total_map = new HashMap<String, Integer>();

		/*
		 * Preload the middle result file.
		 * In the middle result file, each line contains a word and its frequency Freq(A), seperated by "\t"
		 */
		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Path middle_result_path = new Path("mid/part-r-00000");
			Configuration middle_conf = new Configuration();
			try {
				FileSystem fs = FileSystem.get(URI.create(middle_result_path.toString()), middle_conf);

				if (!fs.exists(middle_result_path)) {
					throw new IOException(middle_result_path.toString() + "not exist!");
				}

				FSDataInputStream in = fs.open(middle_result_path);
				InputStreamReader inStream = new InputStreamReader(in);
				BufferedReader reader = new BufferedReader(inStream);

				LOG.info("reading...");
				String line = reader.readLine();
				String[] line_terms;
				while (line != null) {
					line_terms = line.split("\t");
					word_total_map.put(line_terms[0], Integer.valueOf(line_terms[1]));
					LOG.info("read one line!");
					line = reader.readLine();
				}
				reader.close();
				LOG.info("finished！");
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

		/*
		 * TODO: write your second-pass Reducer here.
		 */
		@Override
		protected void reduce(PairOfStrings key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
			/*
			 * TODO: Your implementation goes here.
			 */
			//get the word A and B frequency
			Integer freqA = word_total_map.get(key.getLeftElement());
			Integer freqB = word_total_map.get(key.getRightElement());

			int freq_Bigram = 0;
			//sum up the value of the value in same key
			for (IntWritable value : values) {
				freq_Bigram += value.get();
			}

			// only calculate when both word frequency is larger than 0
		   	if (freqA > 0 && freqB > 0 && freqA != null && freqB != null) {
				//calculate the correlation
				context.write(key, new DoubleWritable((double) freq_Bigram / (freqA * freqB)));
			}
		}
	}

	private static final class MyPartitioner extends Partitioner<PairOfStrings, FloatWritable> {
		@Override
		public int getPartition(PairOfStrings key, FloatWritable value, int numReduceTasks) {
			return (key.getLeftElement().hashCode() & Integer.MAX_VALUE) % numReduceTasks;
		}
	}

	/**
	 * Creates an instance of this tool.
	 */
	public CORPairs() {
	}

	private static final String INPUT = "input";
	private static final String MIDDLE = "middle";
	private static final String OUTPUT = "output";
	private static final String NUM_REDUCERS = "numReducers";

	/**
	 * Runs this tool.
	 */
	@SuppressWarnings({ "static-access" })
	public int run(String[] args) throws Exception {
		Options options = new Options();

		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("input path").create(INPUT));
		options.addOption(OptionBuilder.withArgName("path").hasArg()
				.withDescription("output path").create(OUTPUT));
		options.addOption(OptionBuilder.withArgName("num").hasArg()
				.withDescription("number of reducers").create(NUM_REDUCERS));

		CommandLine cmdline;
		CommandLineParser parser = new GnuParser();

		try {
			cmdline = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println("Error parsing command line: "
					+ exp.getMessage());
			return -1;
		}

		// Lack of arguments
		if (!cmdline.hasOption(INPUT) || !cmdline.hasOption(OUTPUT)) {
			System.out.println("args: " + Arrays.toString(args));
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(120);
			formatter.printHelp(this.getClass().getName(), options);
			ToolRunner.printGenericCommandUsage(System.out);
			return -1;
		}

		String inputPath = cmdline.getOptionValue(INPUT);
		String middlePath = "mid";
		String outputPath = cmdline.getOptionValue(OUTPUT);

		int reduceTasks = cmdline.hasOption(NUM_REDUCERS) ? Integer
				.parseInt(cmdline.getOptionValue(NUM_REDUCERS)) : 1;

		LOG.info("Tool: " + CORPairs.class.getSimpleName());
		LOG.info(" - input path: " + inputPath);
		LOG.info(" - output path: " + outputPath);
		LOG.info(" - number of reducers: " + reduceTasks);

		// Setup for the first-pass MapReduce
		Configuration conf1 = new Configuration();

		Job job1 = Job.getInstance(conf1, "Firstpass");

		job1.setJarByClass(CORPairs.class);
		job1.setMapperClass(CORMapper1.class);
		job1.setReducerClass(CORReducer1.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(IntWritable.class);

		FileInputFormat.setInputPaths(job1, new Path(inputPath));
		FileOutputFormat.setOutputPath(job1, new Path(middlePath));

		// Delete the output directory if it exists already.
		Path middleDir = new Path(middlePath);
		FileSystem.get(conf1).delete(middleDir, true);

		// Time the program
		long startTime = System.currentTimeMillis();
		job1.waitForCompletion(true);
		LOG.info("Job 1 Finished in " + (System.currentTimeMillis() - startTime)
				/ 1000.0 + " seconds");

		// Setup for the second-pass MapReduce

		// Delete the output directory if it exists already.
		Path outputDir = new Path(outputPath);
		FileSystem.get(conf1).delete(outputDir, true);


		Configuration conf2 = new Configuration();
		Job job2 = Job.getInstance(conf2, "Secondpass");

		job2.setJarByClass(CORPairs.class);
		job2.setMapperClass(CORPairsMapper2.class);
		job2.setCombinerClass(CORPairsCombiner2.class);
		job2.setReducerClass(CORPairsReducer2.class);

		job2.setOutputKeyClass(PairOfStrings.class);
		job2.setOutputValueClass(DoubleWritable.class);
		job2.setMapOutputValueClass(IntWritable.class);
		job2.setNumReduceTasks(reduceTasks);

		FileInputFormat.setInputPaths(job2, new Path(inputPath));
		FileOutputFormat.setOutputPath(job2, new Path(outputPath));

		// Time the program
		startTime = System.currentTimeMillis();
		job2.waitForCompletion(true);
		LOG.info("Job 2 Finished in " + (System.currentTimeMillis() - startTime)
				/ 1000.0 + " seconds");

		return 0;
	}

	/**
	 * Dispatches command-line arguments to the tool via the {@code ToolRunner}.
	 */
	public static void main(String[] args) throws Exception {
		ToolRunner.run(new CORPairs(), args);
	}
}