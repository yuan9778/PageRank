import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.chain.ChainMapper;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class  UnitMultiplication {

    public static class TransitionMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            // input: 1\t2, 5, 6, 7
            // key = 1
            // value = to=prob
            String line = value.toString().trim();
            String[] fromTo = line.split("\t");

            if(fromTo.length == 1 || fromTo[1].trim().equals("")) {
                return;
            }

            String from = fromTo[0];
            String[] to = fromTo[1].split(",");
            for (String cur : to) {
                context.write(new Text(from), new Text(to + "=" + (double)1 / to.length));
            }
        }
    }

    public static class PRMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            //input format: 1\t1/400
            // key = 1
            // value = 1/400
            String[] pr = value.toString().split("\t");
            context.write(new Text(pr[0]), new Text(pr[1]));
        }
    }

    public static class MultiplicationReducer extends Reducer<Text, Text, Text, Text> {


        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            //key = 1
            //values = [2=1/4, 3=2/7,...,1/400]
            // this reducer does not do merge job

            List<String> transitionUnit = new ArrayList<String>();
            double prUnit = 0;

            for (Text value: values) {
                if(value.toString().contains("=")) {
                    transitionUnit.add(value.toString());
                }
                else {
                    prUnit = Double.parseDouble((value.toString().trim()));
                }
            }

            for (String unit:transitionUnit) {
                String outputKey = unit.split("=")[0];
                double relation = Double.parseDouble(unit.split("=")[1]);
                String outputValue = String.valueOf(relation * prUnit);
                context.write(new Text(outputKey), new Text(outputValue));
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf);
        job.setJarByClass(UnitMultiplication.class);

        //how chain two mapper classes?
        ChainMapper.addMapper(job, TransitionMapper.class, Object.class, Text.class, Text.class, Text.class, conf);
        ChainMapper.addMapper(job, PRMapper.class, Object.class, Text.class, Text.class, Text.class, conf);

        job.setReducerClass(MultiplicationReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, TransitionMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, PRMapper.class);

        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        job.waitForCompletion(true);
    }

}

